/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.dao.aaf.cass;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.att.authz.env.AuthzEnv;
import com.att.authz.env.AuthzTrans;
import com.att.authz.layer.Result;
import com.att.cadi.CadiException;
import com.att.cadi.SecuritySetter;
import com.att.cadi.client.Future;
import com.att.cadi.client.Rcli;
import com.att.cadi.client.Retryable;
import com.att.cadi.http.HMangr;
import com.att.dao.AbsCassDAO;
import com.att.dao.CIDAO;
import com.att.dao.CassAccess;
import com.att.dao.CassDAOImpl;
import com.att.dao.Loader;
import com.att.inno.env.APIException;
import com.att.inno.env.Env;
import com.att.inno.env.TimeTaken;
import com.att.inno.env.Trans;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;

public class CacheInfoDAO extends CassDAOImpl<AuthzTrans,CacheInfoDAO.Data> implements CIDAO<AuthzTrans> {

	private static final String TABLE = "cache";
	public static final Map<String,Date[]> info = new ConcurrentHashMap<String,Date[]>();

	private static CacheUpdate cacheUpdate;
	
	
	private BoundStatement check;
	// Hold current time stamps from Tables
	private final Date startTime;
	
	public CacheInfoDAO(AuthzTrans trans, Cluster cluster, String keyspace) throws APIException, IOException {
		super(trans, CacheInfoDAO.class.getSimpleName(),cluster,keyspace,Data.class,TABLE,readConsistency(trans,TABLE), writeConsistency(trans,TABLE));
		startTime = new Date();
		init(trans);
	}

	public CacheInfoDAO(AuthzTrans trans, AbsCassDAO<AuthzTrans,?> aDao) throws APIException, IOException {
		super(trans, CacheInfoDAO.class.getSimpleName(),aDao,Data.class,TABLE,readConsistency(trans,TABLE), writeConsistency(trans,TABLE));
		startTime = new Date();
		init(trans);
	}


    //////////////////////////////////////////
    // Data Definition, matches Cassandra DM
    //////////////////////////////////////////
    private static final int KEYLIMIT = 2;
	/**
     */
	public static class Data {
		public Data() {
			name = null;
			touched = null;
		}
		public Data(String name, int seg) {
			this.name = name;
			this.seg = seg;
			touched = null;
		}
		
		public String		name;
		public int			seg;
		public Date			touched;
    }

    private static class InfoLoader extends Loader<Data> {
    	public static final InfoLoader dflt = new InfoLoader(KEYLIMIT);
    	
		public InfoLoader(int keylimit) {
			super(keylimit);
		}
		
		@Override
		public Data load(Data data, Row row) {
			// Int more efficient
			data.name = row.getString(0);
			data.seg = row.getInt(1);
			data.touched = row.getDate(2);
			return data;
		}

		@Override
		protected void key(Data data, int _idx, Object[] obj) {
		    	int idx = _idx;

			obj[idx]=data.name;
			obj[++idx]=data.seg;
		}

		@Override
		protected void body(Data data, int idx, Object[] obj) {
			obj[idx]=data.touched;
		}
    }
    
	public static<T extends Trans> void startUpdate(AuthzEnv env, HMangr hman, SecuritySetter<HttpURLConnection> ss, String ip, int port) {
		if(cacheUpdate==null) {
			Thread t= new Thread(cacheUpdate = new CacheUpdate(env,hman,ss, ip,port),"CacheInfo Update Thread");
			t.setDaemon(true);
			t.start();
		}
	}

	public static<T extends Trans> void stopUpdate() {
		if(cacheUpdate!=null) {
			cacheUpdate.go=false;
		}
	}

	private final static class CacheUpdate extends Thread {
		public static BlockingQueue<Transfer> notifyDQ = new LinkedBlockingQueue<Transfer>(2000);

		private static final String VOID_CT="application/Void+json;q=1.0;charset=utf-8;version=2.0,application/json;q=1.0;version=2.0,*/*;q=1.0";
		private AuthzEnv env;
		private HMangr hman;
		private SecuritySetter<HttpURLConnection> ss;
		private final String authority;
		public boolean go = true;
		
		public CacheUpdate(AuthzEnv env, HMangr hman, SecuritySetter<HttpURLConnection> ss, String ip, int port) {
			this.env = env;
			this.hman = hman;
			this.ss = ss;
			
			this.authority = ip+':'+port;
		}
		
		private static class Transfer {
			public String table;
			public int segs[];
			public Transfer(String table, int[] segs)  {
				this.table = table;
				this.segs = segs;
			}
		}
		private class CacheClear extends Retryable<Integer> {
			public int total=0;
			private AuthzTrans trans;
			private String type;
			private String segs;
			
			public CacheClear(AuthzTrans trans) {
				this.trans = trans;
			}

			public void set(Entry<String, IntHolder> es) {
				type = es.getKey();
				segs = es.getValue().toString();
			}
			
		@Override
			public Integer code(Rcli<?> client) throws APIException, CadiException {
				URI to = client.getURI();
				if(!to.getAuthority().equals(authority)) {
					Future<Void> f = client.delete("/mgmt/cache/"+type+'/'+segs,VOID_CT);
					if(f.get(hman.readTimeout())) {
					    ++total;
					} else {
					    trans.error().log("Error During AAF Peer Notify",f.code(),f.body());
					}
				}
				return total;
			}
		}
		
		private class IntHolder {
			private int[] raw;
			HashSet<Integer> set;
			
			public IntHolder(int ints[]) {
				raw = ints;
				set = null;
			}
			public void add(int[] ints) {
				if(set==null) {
					set = new HashSet<Integer>();
					
					for(int i=0;i<raw.length;++i) {
						set.add(raw[i]);
					}
				}
				for(int i=0;i<ints.length;++i) {
					set.add(ints[i]);
				}
			}

			@Override
			public String toString() {
				StringBuilder sb = new StringBuilder();
				boolean first = true;
				if(set==null) {
					for(int i : raw) {
						if(first) {
							first=false;
						} else {
							sb.append(',');
						}
						sb.append(i);
					}
				} else {
					for(Integer i : set) {
						if(first) {
							first=false;
						} else {
							sb.append(',');
						}
						sb.append(i);
					}
				}
				return sb.toString();
			}
		}
		
		@Override
		public void run() {
			do {
				try {
					Transfer data = notifyDQ.poll(4,TimeUnit.SECONDS);
					if(data==null) {
						continue;
					}
					
					int count = 0;
					CacheClear cc = null;
					Map<String,IntHolder> gather = null;
					AuthzTrans trans = null;
					long start=0;
					// Do a block poll first
					do {
						if(gather==null) {
							start = System.nanoTime();
							trans = env.newTransNoAvg();
							cc = new CacheClear(trans);
							gather = new HashMap<String,IntHolder>();
						}
						IntHolder prev = gather.get(data.table);
						if(prev==null) {
							gather.put(data.table,new IntHolder(data.segs));
						} else {
							prev.add(data.segs);
						}
						// continue while there is data
					} while((data = notifyDQ.poll())!=null);
					if(gather!=null) {
						for(Entry<String, IntHolder> es : gather.entrySet()) {
							cc.set(es);
							try {
								if(hman.all(ss, cc, false)!=null) {
									++count;
								}
							} catch (Exception e) {
								trans.error().log(e, "Error on Cache Update");
							}
						}
						if(env.debug().isLoggable()) {
							float millis = (System.nanoTime()-start)/1000000f;
							StringBuilder sb = new StringBuilder("Direct Cache Refresh: ");
							sb.append("Updated ");
							sb.append(count);
							if(count==1) {
								sb.append(" entry for ");
							} else { 
								sb.append(" entries for ");
							}
							int peers = count<=0?0:cc.total/count;
							sb.append(peers);
							sb.append(" client");
							if(peers!=1) {
								sb.append('s');
							}
							sb.append(" in ");
							sb.append(millis);
							sb.append("ms");
							trans.auditTrail(0, sb, Env.REMOTE);
							env.debug().log(sb);
						}
					}
				} catch (InterruptedException e1) {
					go = false;
				}
			} while(go);
		}
	}

	private void init(AuthzTrans trans) throws APIException, IOException {
		
		String[] helpers = setCRUD(trans, TABLE, Data.class, InfoLoader.dflt);
		check = getSession(trans).prepare(SELECT_SP +  helpers[FIELD_COMMAS] + " FROM " + TABLE).bind();

		disable(CRUD.create);
		disable(CRUD.delete);
	}

	/* (non-Javadoc)
	 * @see com.att.dao.aaf.cass.CIDAO#touch(com.att.authz.env.AuthzTrans, java.lang.String, int)
	 */
	
	@Override
	public Result<Void> touch(AuthzTrans trans, String name, int ... seg) {
		/////////////
		// Direct Service Cache Invalidation
		/////////////
		// ConcurrentQueues are open-ended.  We don't want any Memory leaks 
		// Note: we keep a separate counter, because "size()" on a Linked Queue is expensive
		if(cacheUpdate!=null) {
			try {
				if(!CacheUpdate.notifyDQ.offer(new CacheUpdate.Transfer(name, seg),2,TimeUnit.SECONDS)) {
					trans.error().log("Cache Notify Queue is not accepting messages, bouncing may be appropriate" );
				}
			} catch (InterruptedException e) {
				trans.error().log("Cache Notify Queue posting was interrupted" );
			}
		}

		/////////////
		// Table Based Cache Invalidation (original)
		/////////////
		// Note: Save time with multiple Sequence Touches, but PreparedStmt doesn't support IN
		StringBuilder start = new StringBuilder("CacheInfoDAO Touch segments ");
		start.append(name);
		start.append(": ");
		StringBuilder sb = new StringBuilder("BEGIN BATCH\n");
		boolean first = true;
		for(int s : seg) {
			sb.append(UPDATE_SP);
			sb.append(TABLE);
			sb.append(" SET touched=dateof(now()) WHERE name = '");
			sb.append(name);
			sb.append("' AND seg = ");
			sb.append(s);
			sb.append(";\n");	
			if(first) {
				first =false;
			} else {
				start.append(',');
			}
			start.append(s);
		}
		sb.append("APPLY BATCH;");
		TimeTaken tt = trans.start(start.toString(),Env.REMOTE);
		try {
			getSession(trans).executeAsync(sb.toString());
		} catch (DriverException | APIException | IOException e) {
			reportPerhapsReset(trans,e);
			return Result.err(Result.ERR_Backend, CassAccess.ERR_ACCESS_MSG);
		} finally {
			tt.done();
		}
		return Result.ok();
	}

	/* (non-Javadoc)
	 * @see com.att.dao.aaf.cass.CIDAO#check(com.att.authz.env.AuthzTrans)
	 */
	@Override
	public Result<Void> check(AuthzTrans trans) {
		ResultSet rs;
		TimeTaken tt = trans.start("Check Table Timestamps",Env.REMOTE);
		try {
			rs = getSession(trans).execute(check);
		} catch (DriverException | APIException | IOException e) {
			reportPerhapsReset(trans,e);
			return Result.err(Result.ERR_Backend, CassAccess.ERR_ACCESS_MSG);
		} finally {
			tt.done();
		}
		
		String lastName = null;
		Date[] dates = null;
		for(Row row : rs.all()) {
			String name = row.getString(0);
			int seg = row.getInt(1);
			if(!name.equals(lastName)) {
				dates = info.get(name);
				lastName=name;
			}
			if(dates==null) {
				dates=new Date[seg+1];
				info.put(name,dates);
			} else if(dates.length<=seg) {
				Date[] temp = new Date[seg+1];
				System.arraycopy(dates, 0, temp, 0, dates.length);
				dates = temp;
				info.put(name, dates);
			}
			Date temp = row.getDate(2);
			if(dates[seg]==null || dates[seg].before(temp)) {
				dates[seg]=temp;
			}
		}
		return Result.ok();
	}
	
    /* (non-Javadoc)
	 * @see com.att.dao.aaf.cass.CIDAO#get(java.lang.String, int)
	 */
    @Override
	public Date get(AuthzTrans trans, String table, int seg) {
		Date[] dates = info.get(table);
		if(dates==null) {
			dates = new Date[seg+1];
			touch(trans,table, seg);
		} else if(dates.length<=seg) {
			Date[] temp = new Date[seg+1];
			System.arraycopy(dates, 0, temp, 0, dates.length);
			dates = temp;
		}
		Date rv = dates[seg];
		if(rv==null) {
			rv=dates[seg]=startTime;
		}
		return rv;
	}

	@Override
	protected void wasModified(AuthzTrans trans, CRUD modified, Data data, String ... override) {
		// Do nothing
	}

}