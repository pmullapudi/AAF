/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.cadi;

public interface Connector {
	public Lur newLur() throws CadiException;
}
