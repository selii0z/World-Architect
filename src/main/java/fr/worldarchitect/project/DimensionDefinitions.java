package fr.worldarchitect.project;
import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;

public final class DimensionDefinitions {
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private DimensionDefinitions(){}
 public static JsonObject definition(Project project) {

  JsonObject root=new JsonObject();
  root.addProperty("type","minecraft:overworld");
  JsonObject generator=new JsonObject();
  if(project.worldType()==Project.WorldType.NATURAL){
   generator.addProperty("type","minecraft:noise");
   generator.addProperty("settings","minecraft:overworld");
   JsonObject biome=new JsonObject();
   biome.addProperty("type","minecraft:multi_noise");
   biome.addProperty("preset","minecraft:overworld");
   generator.add("biome_source",biome);
  }else{
   generator.addProperty("type","minecraft:flat");
   JsonObject settings=new JsonObject();
   settings.addProperty("biome",(project.worldType()==Project.WorldType.VOID||project.worldType()==Project.WorldType.IMPORTED)?"minecraft:the_void":"minecraft:plains");
   settings.addProperty("features",false);
   settings.addProperty("lakes",false);
   settings.add("structure_overrides",new JsonArray());
   JsonArray layers=new JsonArray();
   if(project.worldType()==Project.WorldType.FLAT){
    layer(layers,"minecraft:bedrock",1);layer(layers,"minecraft:dirt",3);layer(layers,"minecraft:grass_block",1);
   }else layer(layers,"minecraft:air",1);
   settings.add("layers",layers);generator.add("settings",settings);
  }
  root.add("generator",generator);
  return root;
 }
 public static void stage(Project project, Path pack) throws IOException {
  JsonObject root=definition(project);
  Path directory=pack.resolve("data/worldarchitect/dimension");
  Files.createDirectories(directory);
  Path target=directory.resolve(project.dimensionId().substring("worldarchitect:".length())+".json");
  Files.writeString(target,GSON.toJson(root),StandardOpenOption.CREATE_NEW);
 }
 private static void layer(JsonArray layers,String block,int height){
  JsonObject layer=new JsonObject();layer.addProperty("block",block);layer.addProperty("height",height);layers.add(layer);
 }
}
