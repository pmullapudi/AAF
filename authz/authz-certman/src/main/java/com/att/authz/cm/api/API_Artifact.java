/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.authz.cm.api;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.att.aft.dme2.internal.jetty.http.HttpStatus;
import com.att.authz.cm.mapper.Mapper.API;
import com.att.authz.cm.service.CertManAPI;
import com.att.authz.cm.service.Code;
import com.att.authz.env.AuthzTrans;
import com.att.authz.layer.Result;
import com.att.cssa.rserv.HttpMethods;

/**
 * API Deployment Artifact Apis.. using Redirect for mechanism
 * 
 *
 */
public class API_Artifact {
	private static final String GET_ARTIFACTS = "Get Artifacts";

	/**
	 * Normal Init level APIs
	 * 
	 * @param cmAPI
	 * @param facade
	 * @throws Exception
	 */
	public static void init(final CertManAPI cmAPI) throws Exception {
		cmAPI.route(HttpMethods.POST, "/cert/artifacts", API.ARTIFACTS, new Code(cmAPI,"Create Artifacts") {
			@Override
			public void handle(AuthzTrans trans, HttpServletRequest req, HttpServletResponse resp) throws Exception {
				Result<Void> r = context.createArtifacts(trans, req, resp);
				if(r.isOK()) {
					resp.setStatus(HttpStatus.CREATED_201);
				} else {
					context.error(trans,resp,r);
				}
			}
		});
		
		cmAPI.route(HttpMethods.GET, "/cert/artifacts/:mechid/:machine", API.ARTIFACTS, new Code(cmAPI,GET_ARTIFACTS) {
			@Override
			public void handle(AuthzTrans trans, HttpServletRequest req, HttpServletResponse resp) throws Exception {
				
				Result<Void> r = context.readArtifacts(trans, resp, pathParam(req,":mechid"), pathParam(req,":machine"));
				if(r.isOK()) {
					resp.setStatus(HttpStatus.CREATED_201);
				} else {
					context.error(trans,resp,r);
				}
			}
		});

		cmAPI.route(HttpMethods.GET, "/cert/artifacts", API.ARTIFACTS, new Code(cmAPI,GET_ARTIFACTS) {
			@Override
			public void handle(AuthzTrans trans, HttpServletRequest req, HttpServletResponse resp) throws Exception {
				Result<Void> r = context.readArtifacts(trans, req, resp);
				if(r.isOK()) {
					resp.setStatus(HttpStatus.CREATED_201);
				} else {
					context.error(trans,resp,r);
				}
			}
		});

		cmAPI.route(HttpMethods.PUT, "/cert/artifacts", API.ARTIFACTS, new Code(cmAPI,"Update Artifacts") {
			@Override
			public void handle(AuthzTrans trans, HttpServletRequest req, HttpServletResponse resp) throws Exception {
				Result<Void> r = context.updateArtifacts(trans, req, resp);
				if(r.isOK()) {
					resp.setStatus(HttpStatus.OK_200);
				} else {
					context.error(trans,resp,r);
				}
			}
		});

		cmAPI.route(HttpMethods.DELETE, "/cert/artifacts/:mechid/:machine", API.VOID, new Code(cmAPI,"Delete Artifacts") {
			@Override
			public void handle(AuthzTrans trans, HttpServletRequest req, HttpServletResponse resp) throws Exception {
				Result<Void> r = context.deleteArtifacts(trans, resp, 
						pathParam(req, ":mechid"), pathParam(req,":machine"));
				if(r.isOK()) {
					resp.setStatus(HttpStatus.OK_200);
				} else {
					context.error(trans,resp,r);
				}
			}
		});
		

		cmAPI.route(HttpMethods.DELETE, "/cert/artifacts", API.VOID, new Code(cmAPI,"Delete Artifacts") {
			@Override
			public void handle(AuthzTrans trans, HttpServletRequest req, HttpServletResponse resp) throws Exception {
				Result<Void> r = context.deleteArtifacts(trans, req, resp);
				if(r.isOK()) {
					resp.setStatus(HttpStatus.OK_200);
				} else {
					context.error(trans,resp,r);
				}
			}
		});
		

	}
}
