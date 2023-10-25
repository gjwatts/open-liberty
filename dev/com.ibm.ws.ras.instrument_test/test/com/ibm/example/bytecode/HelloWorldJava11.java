package com.ibm.example.bytecode;

public class HelloWorldJava11 {
  public static void printHi() {
    System.out.println("hi");
  }
  
  public static int addThingsStatic(int a, int b) {
    int c = a + b;
    return c;
  }
  
  public int addThings(int a, int b) {
    int c = a + b;
    return c;
  }

  public static void javaSpecificTest() {  // JEP 323 - introduced in Java 11
    ArrayList<String> alphabet = new ArrayList<String>(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"));
    alphabet.forEach( (var n) -> { System.out.println(n); } );
  }
  
  public Object instancer(Class blah) throws IllegalAccessException, InstantiationException {
    return blah.newInstance();
  }
}
