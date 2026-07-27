package net.minecraft.client.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.resources.VanillaPack;
import net.minecraft.util.ResourceLocation;

public class VirtualAssetsPack extends VanillaPack
{
    private final ResourceIndex field_195785_b;

    public VirtualAssetsPack(ResourceIndex p_i48115_1_)
    {
        // mmdskin：注册 mmdskin 命名空间，使 assets/mmdskin/lang 等经资源管理器加载。
        // YSM：同理注册 yes_steve_model —— 没有它 SimpleReloadableResourceManager 不会为该命名空间
        // 建 FallbackResourceManager，于是 lang/*.json（ClientLanguageMap 只遍历已注册命名空间）与
        // yes_steve_model:texture/roulette.png（轮盘 GUI 的滑条/复选框贴图）全部解析失败。
        // 已用 VanillaPack 探针实测：声明命名空间后两者均可从 classpath 命中。
        super("minecraft", "mmdskin", "yes_steve_model");
        this.field_195785_b = p_i48115_1_;
    }

    @Nullable
    protected InputStream getInputStreamVanilla(ResourcePackType type, ResourceLocation location)
    {
        if (type == ResourcePackType.CLIENT_RESOURCES)
        {
            File file1 = this.field_195785_b.getFile(location);

            if (file1 != null && file1.exists())
            {
                try
                {
                    return new FileInputStream(file1);
                }
                catch (FileNotFoundException filenotfoundexception)
                {
                }
            }
        }

        return super.getInputStreamVanilla(type, location);
    }

    public boolean resourceExists(ResourcePackType type, ResourceLocation location)
    {
        if (type == ResourcePackType.CLIENT_RESOURCES)
        {
            File file1 = this.field_195785_b.getFile(location);

            if (file1 != null && file1.exists())
            {
                return true;
            }
        }

        return super.resourceExists(type, location);
    }

    @Nullable
    protected InputStream getInputStreamVanilla(String pathIn)
    {
        File file1 = this.field_195785_b.getFile(pathIn);

        if (file1 != null && file1.exists())
        {
            try
            {
                return new FileInputStream(file1);
            }
            catch (FileNotFoundException filenotfoundexception)
            {
            }
        }

        return super.getInputStreamVanilla(pathIn);
    }

    public Collection<ResourceLocation> getAllResourceLocations(ResourcePackType type, String namespaceIn, String pathIn, int maxDepthIn, Predicate<String> filterIn)
    {
        Collection<ResourceLocation> collection = super.getAllResourceLocations(type, namespaceIn, pathIn, maxDepthIn, filterIn);
        collection.addAll(this.field_195785_b.getFiles(pathIn, namespaceIn, maxDepthIn, filterIn));
        return collection;
    }
}
