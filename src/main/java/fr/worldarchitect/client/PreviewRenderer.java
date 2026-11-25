package fr.worldarchitect.client;

import fr.worldarchitect.network.ProjectPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid="worldarchitect",value=Dist.CLIENT)
public final class PreviewRenderer {
 private PreviewRenderer(){}
 @SubscribeEvent public static void render(RenderLevelStageEvent event){
  if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
  var mc=Minecraft.getInstance();
  var result=ProjectPackets.latest();
  if(mc.level==null||result==null||!result.success()||result.preview()==null)return;
  var p=result.preview();
  if(!mc.level.dimension().location().toString().equals(p.dimension()))return;
  var camera=event.getCamera().getPosition();
  var box=new AABB(p.minX(),p.minY(),p.minZ(),p.maxX()+1.0,p.maxY()+1.0,p.maxZ()+1.0).move(-camera.x,-camera.y,-camera.z);
  var buffers=mc.renderBuffers().bufferSource();
  LevelRenderer.renderLineBox(event.getPoseStack(),buffers.getBuffer(RenderType.lines()),box,p.valid()?0.35f:1f,p.valid()?1f:0.2f,0.35f,0.9f);
  buffers.endBatch(RenderType.lines());
  var blocks=ProjectPackets.ghostBlocks(p.structure());
  // Limit immediate model rendering; large prefabs retain their complete boundary.
  if(blocks.size()>4096)return;
  var rotation=net.minecraft.world.level.block.Rotation.valueOf(p.rotation());
  var mirror=p.mirrored()?net.minecraft.world.level.block.Mirror.FRONT_BACK:net.minecraft.world.level.block.Mirror.NONE;
  var anchor=new net.minecraft.core.BlockPos(p.anchorX(),p.anchorY(),p.anchorZ());
  var pose=event.getPoseStack();
  var ghostBuffer=buffers.getBuffer(RenderType.translucent());
  for(var block:blocks){
   var pos=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(block.offset(),mirror,rotation,net.minecraft.core.BlockPos.ZERO).offset(anchor);
   if(pos.distToCenterSqr(camera.x,camera.y,camera.z)>96*96)continue;
   var state=net.minecraft.world.level.block.Block.stateById(block.stateId()).mirror(mirror).rotate(rotation);
   pose.pushPose();
   try{
    pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
    if(state.getRenderShape()!=net.minecraft.world.level.block.RenderShape.MODEL)continue;
    var model=mc.getBlockRenderer().getBlockModel(state);
    int tint=mc.getBlockColors().getColor(state,mc.level,pos,0);
    float red=tint==-1?1f:((tint>>16)&255)/255f;
    float green=tint==-1?1f:((tint>>8)&255)/255f;
    float blue=tint==-1?1f:(tint&255)/255f;
    var translucent=new com.mojang.blaze3d.vertex.VertexConsumer(){
     public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x,float y,float z){ghostBuffer.addVertex(x,y,z);return this;}
     public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r,int g,int b,int a){ghostBuffer.setColor(r,g,b,Math.round(a*0.45f));return this;}
     public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u,float v){ghostBuffer.setUv(u,v);return this;}
     public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u,int v){return this;}
     public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u,int v){ghostBuffer.setUv2(u,v);return this;}
     public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x,float y,float z){ghostBuffer.setNormal(x,y,z);return this;}
    };
    mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),translucent,state,model,red,green,blue,0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
   }finally{pose.popPose();}
  }
  buffers.endBatch(RenderType.translucent());
 }
}
