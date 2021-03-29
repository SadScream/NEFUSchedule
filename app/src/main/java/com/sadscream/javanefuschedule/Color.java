package com.sadscream.javanefuschedule;

public class Color {
    private int r, g, b;

    public Color(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    @Override
    public String toString() {
        return "rgb("+r+","+g+","+b+")";
    }
}
