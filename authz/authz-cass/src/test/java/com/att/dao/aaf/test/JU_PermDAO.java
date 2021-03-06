/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.dao.aaf.test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.att.authz.layer.Result;
import com.att.dao.aaf.cass.PermDAO;
import com.att.dao.aaf.cass.PermDAO.Data;
import com.att.dao.aaf.cass.RoleDAO;
import com.att.inno.env.APIException;

/**
 * Test the PermissionDAO
 * 
 * Utilize AbsJUCass to initialize and pre-load Cass
 * 
 *
 */
public class JU_PermDAO extends AbsJUCass{

	@Test
	public void test() throws APIException, IOException {
		PermDAO pd = new PermDAO(trans,cluster,"authz");
		try {
			PermDAO.Data data = new PermDAO.Data();
			data.ns = "com.test.ju_perm";
			data.type = "MyType";
			data.instance = "MyInstance";
			data.action = "MyAction";
			data.roles(true).add(data.ns + ".dev");
			


			// CREATE
			Result<Data> rpdc = pd.create(trans,data);
			assertTrue(rpdc.isOK());

			Result<List<PermDAO.Data>> rlpd;
			try {
//		        Bytification
		        ByteBuffer bb = data.bytify();
		        Data bdata = new PermDAO.Data();
		        bdata.reconstitute(bb);
		        compare(data, bdata);

				// Validate Read with key fields in Data
				if((rlpd = pd.read(trans,data)).isOK())
				  for(PermDAO.Data d : rlpd.value) {
					checkData1(data,d);
				}
				
				// Validate readByName
				if((rlpd = pd.readByType(trans,data.ns, data.type)).isOK())
				  for(PermDAO.Data d : rlpd.value) {
					checkData1(data,d);
				}
				
				// Add Role
				RoleDAO.Data role = new RoleDAO.Data();
				role.ns = data.ns;
				role.name = "test";
				
				Result<Void> rvpd = pd.addRole(trans, data, role.fullName());
				assertTrue(rvpd.isOK());
				// Validate Read with key fields in Data
				if((rlpd = pd.read(trans,data)).isOK())
				  for(PermDAO.Data d : rlpd.value) {
					checkData2(data,d);
				  }
				
				// Remove Role
				rvpd = pd.delRole(trans, data, role.fullName());
				assertTrue(rvpd.isOK());
				if((rlpd = pd.read(trans,data)).isOK())
					for(PermDAO.Data d : rlpd.value) {
						checkData1(data,d);
					}
				
				// Add Child
				Data data2 = new Data();
				data2.ns = data.ns;
				data2.type = data.type + ".2";
				data2.instance = data.instance;
				data2.action = data.action;
				
				rpdc = pd.create(trans, data2);
				assertTrue(rpdc.isOK());
				try {
					rlpd = pd.readChildren(trans, data.ns,data.type);
					assertTrue(rlpd.isOKhasData());
					assertEquals(rlpd.value.size(),1);
					assertEquals(rlpd.value.get(0).fullType(),data2.fullType());
				} finally {
					// Delete Child
					pd.delete(trans, data2,true);

				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				// DELETE
				Result<Void> rpdd = pd.delete(trans,data,true);
				assertTrue(rpdd.isOK());
				rlpd = pd.read(trans, data);
				assertTrue(rlpd.isOK() && rlpd.isEmpty());
				assertEquals(rlpd.value.size(),0);
			}
		} finally {
			pd.close(trans);
		}
	}

	private void compare(Data a, Data b) {
		assertEquals(a.ns,b.ns);
		assertEquals(a.type,b.type);
		assertEquals(a.instance,b.instance);
		assertEquals(a.action,b.action);
		assertEquals(a.roles(false).size(),b.roles(false).size());
		for(String s: a.roles(false)) {
			assertTrue(b.roles(false).contains(s));
		}
	}
	private void checkData1(Data data, Data d) {
		assertEquals(data.ns,d.ns);
		assertEquals(data.type,d.type);
		assertEquals(data.instance,d.instance);
		assertEquals(data.action,d.action);
		
		Set<String> ss = d.roles(true);
		assertEquals(1,ss.size());
		assertTrue(ss.contains(data.ns+".dev"));
	}
	
	private void checkData2(Data data, Data d) {
		assertEquals(data.ns,d.ns);
		assertEquals(data.type,d.type);
		assertEquals(data.instance,d.instance);
		assertEquals(data.action,d.action);
		
		Set<String> ss = d.roles(true);
		assertEquals(2,ss.size());
		assertTrue(ss.contains(data.ns+".dev"));
		assertTrue(ss.contains(data.ns+".test"));
	}


}
