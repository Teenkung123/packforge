package com.teenkung.packforge.mixin.loader;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.teenkung.packforge.loader.ReloadDirectoryIndex;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

@Mixin(PathPackResources.class)
public abstract class PathPackResourcesMixin {
    @WrapOperation(
        method = "listPath(Ljava/lang/String;Ljava/nio/file/Path;Ljava/util/List;Lnet/minecraft/server/packs/PackResources$ResourceOutput;)V",
        at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;find(Ljava/nio/file/Path;ILjava/util/function/BiPredicate;[Ljava/nio/file/FileVisitOption;)Ljava/util/stream/Stream;", remap = false)
    )
    private static Stream<Path> packforge$listDirectory(Path start, int depth,
            BiPredicate<Path, BasicFileAttributes> predicate, FileVisitOption[] options,
            Operation<Stream<Path>> original, @Local(argsOnly = true) Path namespaceRoot) throws IOException {
        return ReloadDirectoryIndex.list(namespaceRoot, start, depth, predicate, options,
            (path, maxDepth, filter, visits) -> original.call(path, maxDepth, filter, visits));
    }
}
