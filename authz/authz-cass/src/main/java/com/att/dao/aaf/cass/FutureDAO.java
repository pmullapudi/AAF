/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.dao.aaf.cass;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.att.authz.env.AuthzTrans;
import com.att.authz.layer.Result;
import com.att.dao.CassDAOImpl;
import com.att.dao.DAOException;
import com.att.dao.Loader;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;

/**
 * FutureDAO stores Construction information to create 
 * elements at another time.
 * 
 * 8/20/2013
 */
public class FutureDAO extends CassDAOImpl<AuthzTrans,FutureDAO.Data> {
    private static final String TABLE = "future";
	private final HistoryDAO historyDAO;
//	private static String createString;
	private PSInfo psByStartAndTarget;
	
    public FutureDAO(AuthzTrans trans, Cluster cluster, String keyspace) {
        super(trans, FutureDAO.class.getSimpleName(),cluster, keyspace, Data.class,TABLE, readConsistency(trans,TABLE), writeConsistency(trans,TABLE));
		historyDAO = new HistoryDAO(trans, this);
        init(trans);
    }

    public FutureDAO(AuthzTrans trans, HistoryDAO hDAO) {
        super(trans, FutureDAO.class.getSimpleName(),hDAO, Data.class,TABLE, readConsistency(trans,TABLE), writeConsistency(trans,TABLE));
        historyDAO=hDAO;
        init(trans);
    }

    public static final int KEYLIMIT = 1;
    public static class Data {
        public UUID         id;
        public String		target;
        public String		memo;
        public Date       	start;
        public Date       	expires;
        public ByteBuffer 	construct;  //   this is a blob in cassandra
    }

    private static class FLoader extends Loader<Data> {
        public FLoader() {
            super(KEYLIMIT);
        }

        public FLoader(int keylimit) {
            super(keylimit);
        }

        @Override
	public Data load(Data data, Row row) {
            data.id 		= row.getUUID(0);
            data.target		= row.getString(1);
            data.memo       = row.getString(2);
            data.start 		= row.getDate(3);
            data.expires 	= row.getDate(4);
            data.construct 	= row.getBytes(5);
            return data;
        }

        @Override
        protected void key(Data data, int idx, Object[] obj) {
            obj[idx] = data.id;
        }

        @Override
        protected void body(Data data, int _idx, Object[] obj) {
	    int idx = _idx;

            obj[idx] = data.target;
            obj[++idx] = data.memo;
            obj[++idx] = data.start;
            obj[++idx] = data.expires;
            obj[++idx] = data.construct;
        }
    }

    private void init(AuthzTrans trans) {
        // Set up sub-DAOs
        String[] helpers = setCRUD(trans, TABLE, Data.class, new FLoader(KEYLIMIT));

        // Uh, oh.  Can't use "now()" in Prepared Statements (at least at this level)
//		createString = "INSERT INTO " + TABLE + " ("+helpers[FIELD_COMMAS] +") VALUES (now(),";
//
//		// Need a specialty Creator to handle the "now()"
//		replace(CRUD.Create, new PSInfo(trans, "INSERT INTO future (" +  helpers[FIELD_COMMAS] +
//					") VALUES(now(),?,?,?,?,?)",new FLoader(0)));
		
		// Other SELECT style statements... match with a local Method
		psByStartAndTarget = new PSInfo(trans, SELECT_SP + helpers[FIELD_COMMAS] +
				" FROM future WHERE start <= ? and target = ? ALLOW FILTERING", new FLoader(2) {
			@Override
			protected void key(Data data, int _idx, Object[] obj) {
			    	int idx = _idx;

				obj[idx]=data.start;
				obj[++idx]=data.target;
			}
		},readConsistency);
		

    }

    public Result<List<Data>> readByStartAndTarget(AuthzTrans trans, Date start, String target) throws DAOException {
		return psByStartAndTarget.read(trans, R_TEXT, new Object[]{start, target});
	}

    /**
	 * Override create to add secondary ID to Subject in History, and create Data.ID, if it is null
     */
	public Result<FutureDAO.Data> create(AuthzTrans trans,	FutureDAO.Data data, String id) {
		// If ID is not set (typical), create one.
		if(data.id==null) {
			StringBuilder sb = new StringBuilder(trans.user());
			sb.append(data.target);
			sb.append(System.currentTimeMillis());
			data.id = UUID.nameUUIDFromBytes(sb.toString().getBytes());
		}
		Result<ResultSet> rs = createPS.exec(trans, C_TEXT, data);
		if(rs.notOK()) {
			return Result.err(rs);
		}
		wasModified(trans, CRUD.create, data, null, id);
		return Result.ok(data);	
	}

	/**
	 * Log Modification statements to History
	 *
	 * @param modified        which CRUD action was done
	 * @param data            entity data that needs a log entry
	 * @param overrideMessage if this is specified, we use it rather than crafting a history message based on data
	 */
	@Override
	protected void wasModified(AuthzTrans trans, CRUD modified, Data data, String ... override) {
		boolean memo = override.length>0 && override[0]!=null;
		boolean subject = override.length>1 && override[1]!=null;
		HistoryDAO.Data hd = HistoryDAO.newInitedData();
	    hd.user = trans.user();
		hd.action = modified.name();
		hd.target = TABLE;
		hd.subject = subject?override[1]:"";
	    hd.memo = memo?String.format("%s by %s", override[0], hd.user):data.memo;
	
		if(historyDAO.create(trans, hd).status!=Status.OK) {
	    	trans.error().log("Cannot log to History");
		}
	}
    
}
