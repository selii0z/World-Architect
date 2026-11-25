package fr.worldarchitect.project;

public record ProjectBounds(int length,int width) {
 public ProjectBounds {
  if(length==0&&width==0){}else if(length<16||length>60000||width<16||width>60000)throw new IllegalArgumentException("Invalid rectangular bounds");
 }
 public boolean unlimited(){return length==0&&width==0;}
 public boolean contains(double x,double z){
  return Double.isFinite(x)&&Double.isFinite(z)&&(unlimited()||(x>=-length/2.0&&x<length/2.0&&z>=-width/2.0&&z<width/2.0));
 }
 public int enclosingSquare(){return unlimited()?0:Math.max(length,width);}
 public long area(){return (long)length*width;}
}
