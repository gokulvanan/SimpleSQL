package org.simplesql.parser.tree;

public class VARIABLE extends TERM{

	final String name;

	public VARIABLE(String name) {
		super();
		this.name = name;
	}

	public String getName() {
		return name;
	}
	
	
}
