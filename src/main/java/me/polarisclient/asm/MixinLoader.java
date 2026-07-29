package me.polarisclient.asm;

import java.util.Map;
import me.polarisclient.Polaris;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

@Name("Polaris")
@MCVersion("1.12.2")
public class MixinLoader implements IFMLLoadingPlugin {
   private static boolean isObfuscatedEnvironment;

   public MixinLoader() {
      Polaris.LOGGER.info("Loading polaris mixins...\n");
      MixinBootstrap.init();
      Mixins.addConfiguration("mixins.polaris.json");
      MixinEnvironment.getDefaultEnvironment().setObfuscationContext("searge");
      Polaris.LOGGER.info(MixinEnvironment.getDefaultEnvironment().getObfuscationContext());
   }

   public String[] getASMTransformerClass() {
      return new String[0];
   }

   public String getModContainerClass() {
      return null;
   }

   public String getSetupClass() {
      return null;
   }

   public void injectData(Map<String, Object> data) {
      isObfuscatedEnvironment = (Boolean) data.get("runtimeDeobfuscationEnabled");
   }

   public String getAccessTransformerClass() {
      return null;
   }
}
