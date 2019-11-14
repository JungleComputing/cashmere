package example.vectoradd;

public class VectorAddResult implements java.io.Serializable {

  private static final long serialVersionUID = 1L;

  float[] c;
  int offsetInParent;

  public VectorAddResult(float[] c, int offsetInParent) {
    this.c = c;
    this.offsetInParent = offsetInParent;
  }

  public synchronized void add(VectorAddResult other) {
    for (int i = 0; i < other.c.length; i++) {
      this.c[i + other.offsetInParent] = other.c[i];
    }
  }
}
