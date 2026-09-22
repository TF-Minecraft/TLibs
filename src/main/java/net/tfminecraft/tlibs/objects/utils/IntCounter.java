package net.tfminecraft.tlibs.objects.utils;

public class IntCounter {
	private int current;
	private int needed;
	
	public IntCounter() {
		current = 0;
		needed = 0;
	}
	
	public int getCurrent() {
		return current;
	}

	public void setCurrent(int current) {
		this.current = current;
	}
	
	public int getNeeded() {
		return needed;
	}

	public void setNeeded(int needed) {
		this.needed = needed;
	}
	
	public void increaseNeeded(int i) {
		needed = needed+i;
	}

	public void increaseCurrent(int i) {
		current = current+i;
	}
	
	public boolean isEqual() {
		if(current == needed) return true;
		return false;
	}
	
	public double getPercentage() {
		if(needed == 0) return 0.0;
		double p = Math.round(((double) current/needed)*100);
		return p;
	}
}
