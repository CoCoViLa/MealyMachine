class Transition {
	/*@ 
	specification Transition  {
		String from, to;
		String condition, action;
		String id;
		int order;
		from, order -> id {id};
	}
	@*/
	
	String id (String from, int order) {
		return from+" "+order;
	}
};