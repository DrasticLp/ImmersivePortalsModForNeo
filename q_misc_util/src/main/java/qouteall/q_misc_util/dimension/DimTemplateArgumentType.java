package qouteall.q_misc_util.dimension;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * All command argument types will be in registry and synced by Fabric API.
 * To make q_misc_util be able to work server-only, synced registry should not differ.
 */
@Deprecated
public class DimTemplateArgumentType implements ArgumentType<DimensionTemplate> {
    
    public static final DimTemplateArgumentType INSTANCE = new DimTemplateArgumentType();
    
    private static final DynamicCommandExceptionType EXCEPTION_TYPE =
        new DynamicCommandExceptionType(object ->
            Component.literal("Invalid Dim Template " + object)
        );
    
    public static DimensionTemplate getDimTemplate(CommandContext<?> context, String argName) {
        return context.getArgument(argName, DimensionTemplate.class);
    }
    
    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(RegisterEvent.class, registerEvent -> {
            registerEvent.register(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key(),
                    new ResourceLocation("q_misc_util:dim_template"),
                    () -> SingletonArgumentInfo.contextFree(() -> DimTemplateArgumentType.INSTANCE));
        });
    }
    
    @Override
    public DimensionTemplate parse(StringReader reader) throws CommandSyntaxException {
        String s = reader.readUnquotedString();
        
        DimensionTemplate r = DimensionTemplate.DIMENSION_TEMPLATES.get(s);
        
        if (r == null) {
            throw EXCEPTION_TYPE.createWithContext(reader, s);
        }
        
        return r;
    }
    
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
        CommandContext<S> context, SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
            DimensionTemplate.DIMENSION_TEMPLATES.keySet(),
            builder
        );
    }
    
    @Override
    public Collection<String> getExamples() {
        return DimensionTemplate.DIMENSION_TEMPLATES.keySet();
    }
}
