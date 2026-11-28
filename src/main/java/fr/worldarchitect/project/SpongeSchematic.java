package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.*;

/** Converts Sponge schematics into vanilla structure NBT without entities. */
public final class SpongeSchematic {
    private SpongeSchematic() {}

    public static CompoundTag read(Path source) throws IOException {
        CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.create(64L * 1024 * 1024));
        if (root.contains("Schematic", 10)) root = root.getCompound("Schematic");
        int version = root.getInt("Version");
        if (version != 2 && version != 3) throw new IOException("Unsupported Sponge schematic version");
        int w = root.getShort("Width"), h = root.getShort("Height"), d = root.getShort("Length");
        long volume = (long) w * h * d;
        if (w <= 0 || h <= 0 || d <= 0 || volume > 262144) throw new IOException("Schematic exceeds prefab limits");
        CompoundTag section = version == 3 ? root.getCompound("Blocks") : root;
        CompoundTag sourcePalette = section.getCompound("Palette");
        int count = sourcePalette.getAllKeys().size();
        if (count == 0 || count > 65536) throw new IOException("Invalid palette");
        CompoundTag[] states = new CompoundTag[count];
        for (String key : sourcePalette.getAllKeys()) {
            int index = sourcePalette.getInt(key);
            if (index < 0 || index >= count || states[index] != null) throw new IOException("Invalid palette index");
            states[index] = state(key);
        }
        ListTag palette = new ListTag();
        for (CompoundTag state : states) palette.add(state);
        byte[] bytes = section.getByteArray(version == 3 ? "Data" : "BlockData");
        int cursor = 0;
        ListTag blocks = new ListTag();
        for (int i = 0; i < volume; i++) {
            int index = 0;
            boolean complete = false;
            for (int shift = 0; shift < 28; shift += 7) {
                if (cursor >= bytes.length) throw new IOException("Truncated block data");
                int b = bytes[cursor++] & 255;
                index |= (b & 127) << shift;
                if ((b & 128) == 0) { complete = true; break; }
            }
            if (!complete || index >= count) throw new IOException("Invalid block index");
            CompoundTag block = new CompoundTag();
            block.put("pos", ints(i % w, i / (w * d), (i / w) % d));
            block.putInt("state", index);
            blocks.add(block);
        }
        if (cursor != bytes.length) throw new IOException("Unexpected trailing block data");
        CompoundTag result = new CompoundTag();
        result.put("size", ints(w,h,d));
        result.put("palette", palette);
        result.put("blocks", blocks);
        result.put("entities", new ListTag());
        result.putInt("DataVersion", root.getInt("DataVersion"));
        return result;
    }

    private static ListTag ints(int... values) {
        ListTag result = new ListTag();
        for (int value : values) result.add(IntTag.valueOf(value));
        return result;
    }

    private static CompoundTag state(String raw) throws IOException {
        CompoundTag result = new CompoundTag();
        int start = raw.indexOf('[');
        String name = start < 0 ? raw : raw.substring(0,start);
        if (!name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IOException("Invalid block name");
        result.putString("Name", name);
        if (start >= 0) {
            if (!raw.endsWith("]")) throw new IOException("Invalid block properties");
            CompoundTag properties = new CompoundTag();
            for (String pair : raw.substring(start+1,raw.length()-1).split(",")) {
                String[] parts = pair.split("=", -1);
                if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty() || properties.contains(parts[0]))
                    throw new IOException("Invalid block property");
                properties.putString(parts[0],parts[1]);
            }
            result.put("Properties", properties);
        }
        return result;
    }
}
