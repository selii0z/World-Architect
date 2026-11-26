package fr.worldarchitect.project;

import net.minecraft.core.BlockPos;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class EditGeometry {
    private EditGeometry() {}
    public enum Shape { SOLID, WALLS, SHELL, ELLIPSOID, HOLLOW_ELLIPSOID, CYLINDER, HOLLOW_CYLINDER }
    public static Iterable<BlockPos> positions(BlockPos min, BlockPos max, Shape shape) {
        long w=(long)max.getX()-min.getX()+1,h=(long)max.getY()-min.getY()+1,d=(long)max.getZ()-min.getZ()+1;
        if(w<=0||h<=0||d<=0||w>262144||h>262144||d>262144||w*h*d>262144)
            throw new IllegalArgumentException("Invalid edit bounds");
        int width=(int)w,height=(int)h,depth=(int)d,volume=(int)(w*h*d);
        return () -> new Iterator<BlockPos>() {
            int cursor;
            BlockPos next;
            private void advance(){
                while(next==null&&cursor<volume){
                    int i=cursor++,x=i%width,y=i/(width*depth),z=(i/width)%depth;
                    if(matches(x,y,z,width,height,depth,shape))
                        next=min.offset(x,y,z);
                }
            }
            public boolean hasNext(){advance();return next!=null;}
            public BlockPos next(){advance();if(next==null)throw new NoSuchElementException();BlockPos result=next;next=null;return result;}
        };
    }
    private static boolean inside(int x,int y,int z,int w,int h,int d,boolean cylinder){
        if(x<0||x>=w||y<0||y>=h||z<0||z>=d)return false;
        double a=(2.0*x+1-w)/w,b=(2.0*y+1-h)/h,c=(2.0*z+1-d)/d;
        return a*a+c*c+(cylinder?0:b*b)<=1.0;
    }
    private static boolean matches(int x,int y,int z,int w,int h,int d,Shape shape){
        boolean wall=x==0||x==w-1||z==0||z==d-1;
        if(shape==Shape.SOLID)return true;
        if(shape==Shape.WALLS)return wall;
        if(shape==Shape.SHELL)return wall||y==0||y==h-1;
        boolean cylinder=shape==Shape.CYLINDER||shape==Shape.HOLLOW_CYLINDER;
        if(!inside(x,y,z,w,h,d,cylinder))return false;
        if(shape==Shape.ELLIPSOID||shape==Shape.CYLINDER)return true;
        return !inside(x-1,y,z,w,h,d,cylinder)||!inside(x+1,y,z,w,h,d,cylinder)
            ||!inside(x,y,z-1,w,h,d,cylinder)||!inside(x,y,z+1,w,h,d,cylinder)
            ||(!cylinder&&(!inside(x,y-1,z,w,h,d,false)||!inside(x,y+1,z,w,h,d,false)));
    }
}
