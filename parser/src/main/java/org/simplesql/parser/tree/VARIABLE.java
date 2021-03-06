package org.simplesql.parser.tree;

public class VARIABLE extends TERM {

	final String name;

	public VARIABLE(String name) {
		super(TYPE.UKNOWN);
		this.name = name;
		setValue(name);
		setAssignedName(name);
	}

	public String getName() {
		return name;
	}

}
