package modelo;

public class Punto {
	private int x;
	private int y;
	private String color;

	
	public Punto( int y, int x, String color) {
		this.y = y;
		this.x = x;
		this.color = color;
	}


	public int getX() {
		return x;
	}
	public int getY() {
		return y;
	}
	public String getColor() {
		return color;
	}

	public void setX(int x) {
		this.x = x;
	}
	public void setY(int y) {
		this.y = y;
	}
	public void setColor(String color) {
		this.color = color;
	}
}
