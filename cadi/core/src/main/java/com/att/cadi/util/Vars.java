/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.cadi.util;


public class Vars {
	/**
	 * Convert a format string with "%s" into AT&T RESTful Error %1 %2 (number) format
	 * If "holder" is passed in, it is built with full Message extracted (typically for Logging)
	 * @param holder
	 * @param text
	 * @param vars
	 * @return
	 */
	public static String convert(final StringBuilder holder, final String text, final String ... vars) {
		StringBuilder sb = null;
		int idx,index=0,prev = 0;
		
		if(text.contains("%s")) {
			sb = new StringBuilder();
		}
		
		StringBuilder[] sbs = new StringBuilder[] {sb,holder};
		boolean replace, clearIndex = false;
		int c;
		while((idx=text.indexOf('%',prev))>=0) {
			replace = false;
			if(clearIndex) {
				index=0;
			}
			if(sb!=null) {
				sb.append(text,prev,idx);
			}
			if(holder!=null) {
				holder.append(text,prev,idx);
			}
			
			boolean go = true;
			while(go) {
				if(text.length()>++idx) {
					switch(c=text.charAt(idx)) {
						case '0': case '1': case '2': case '3': case '4': 
						case '5': case '6': case '7': case '8': case '9':
							index *=10;
							index +=(c-'0');
							clearIndex=replace=true;
							continue;
						case 's':
							++index;
							replace = true;
							continue;
						default:
							break;
					}
				}
				prev = idx;
				go=false;
				if(replace) {
					if(sb!=null) {
						sb.append('%');
						sb.append(index);
					}
					if(index<=vars.length) {
						if(holder!=null) {
							holder.append(vars[index-1]);
						}
					}
				} else {
					for(StringBuilder s : sbs) {
						if(s!=null) {
							s.append("%");
						}
					}
				}
			}
		}
		
		if(sb!=null) {
			sb.append(text,prev,text.length());
		}
		if(holder!=null) {
			holder.append(text,prev,text.length());
		}

		return sb==null?text:sb.toString();
	}

}
