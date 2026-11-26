package fr.worldarchitect.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

final class BlockPickerScreen extends Screen {
 private final Screen parent;
 private final Consumer<String> choose;
 private final List<Block> blocks=new ArrayList<>();
 private List<Block> filtered=List.of();
 private String query="";
 private int page,columns,rows,left,top;
 BlockPickerScreen(Screen parent,Consumer<String> choose){
  super(Component.literal("Sélectionner un bloc"));
  this.parent=parent;this.choose=choose;
  BuiltInRegistries.BLOCK.forEach(blocks::add);
  blocks.sort(Comparator.comparing(b->BuiltInRegistries.BLOCK.getKey(b).toString()));
 }
 private void search(){
  String q=query.toLowerCase(Locale.ROOT);
  filtered=blocks.stream().filter(b->BuiltInRegistries.BLOCK.getKey(b).toString().contains(q)||b.getName().getString().toLowerCase(Locale.ROOT).contains(q)).toList();
  page=0;
 }
 @Override protected void init(){
  columns=Math.max(1,Math.min(12,(width-24)/24));
  rows=Math.max(1,Math.min(8,(height-100)/24));
  left=(width-columns*24)/2;top=48;
  var field=new EditBox(font,left,22,columns*24,20,Component.literal("Recherche"));
  field.setMaxLength(100);field.setValue(query);field.setResponder(v->{query=v;search();});addRenderableWidget(field);
  search();
  int y=top+rows*24+5;
  addRenderableWidget(Button.builder(Component.literal("<"),b->page=Math.max(0,page-1)).bounds(left,y,24,20).build());
  addRenderableWidget(Button.builder(Component.literal(">"),b->{if((page+1)*columns*rows<filtered.size())page++;}).bounds(left+columns*24-24,y,24,20).build());
  addRenderableWidget(Button.builder(Component.literal("Retour"),b->onClose()).bounds(width/2-35,y+24,70,20).build());
 }
 private int index(int x,int y){
  if(x<left||y<top||x>=left+columns*24||y>=top+rows*24)return -1;
  int i=page*columns*rows+(y-top)/24*columns+(x-left)/24;
  return i<filtered.size()?i:-1;
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){}
 @Override public void render(GuiGraphics g,int mx,int my,float delta){
  g.fill(0,0,width,height,0xff1c201b);
  g.drawCenteredString(font,title,width/2,8,0xffb6c69c);
  int count=columns*rows;
  for(int n=0;n<count&&page*count+n<filtered.size();n++){
   Block b=filtered.get(page*count+n);
   int x=left+n%columns*24,y=top+n/columns*24;
   g.fill(x,y,x+22,y+22,0xff394332);
   ItemStack stack=new ItemStack(b.asItem());
   if(stack.isEmpty())g.drawCenteredString(font,"?",x+11,y+7,0xffe9eadf);
   else g.renderItem(stack,x+3,y+3);
  }
  g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(filtered.size()+count-1)/count)+" — "+filtered.size()+" blocs",width/2,top+rows*24+11,0xffe9eadf);
  super.render(g,mx,my,delta);
  int i=index(mx,my);
  if(i>=0){var b=filtered.get(i);g.renderTooltip(font,Component.literal(b.getName().getString()+" | "+BuiltInRegistries.BLOCK.getKey(b)),mx,my);}
 }
 @Override public boolean mouseClicked(double x,double y,int button){
  int i=index((int)x,(int)y);
  if(button==0&&i>=0){choose.accept(BuiltInRegistries.BLOCK.getKey(filtered.get(i)).toString());onClose();return true;}
  return super.mouseClicked(x,y,button);
 }
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
