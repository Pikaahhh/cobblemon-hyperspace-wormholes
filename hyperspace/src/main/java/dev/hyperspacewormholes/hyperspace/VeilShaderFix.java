package dev.hyperspacewormholes.hyperspace;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Skips Veil's Sodium shader loader mixin when it can't apply. The older Veil packed inside Effective hooks a GlShader
 * constructor that newer Sodium no longer has, which crashes the game as soon as Sodium compiles its own shaders. It's
 * only skipped when that constructor really is missing: both classes are read as bytes and never loaded.
 */
public class VeilShaderFix implements MixinCanceller {
    private static final String VEIL_MIXIN = "foundry.veil.fabric.mixin.compat.sodium.ShaderLoaderMixin";
    private static final String GL_SHADER = "net/caffeinemc/mods/sodium/client/gl/shader/GlShader";
    private static final String STRING_END = "Ljava/lang/String;)V";
    private static final Logger LOGGER = LoggerFactory.getLogger("hyperspace_wormholes");

    private Boolean cancel;

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (!VEIL_MIXIN.equals(mixinClassName)) return false;
        if (cancel == null) {
            cancel = veilWantsStringShader() && !sodiumHasStringShader();
            if (cancel) {
                LOGGER.warn("Skipping Veil's Sodium ShaderLoaderMixin: this Veil (bundled with Effective) hooks a "
                        + "GlShader(String) constructor this Sodium doesn't have, and would crash the game when "
                        + "shaders are off");
            }
        }
        return cancel;
    }

    private static boolean veilWantsStringShader() {
        ClassNode mixin = read(VEIL_MIXIN.replace('.', '/'));
        if (mixin == null) return false;
        for (MethodNode method : mixin.methods) {
            if (aimsAtStringShader(method.visibleAnnotations) || aimsAtStringShader(method.invisibleAnnotations)) {
                return true;
            }
        }
        return false;
    }

    private static boolean aimsAtStringShader(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (aimsAtStringShader(annotation.values)) return true;
        }
        return false;
    }

    private static boolean aimsAtStringShader(Object value) {
        if (value instanceof String text) {
            return text.contains(GL_SHADER + ";<init>(") && text.endsWith(STRING_END);
        }
        if (value instanceof AnnotationNode annotation) return aimsAtStringShader(annotation.values);
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (aimsAtStringShader(item)) return true;
            }
        }
        return false;
    }

    private static boolean sodiumHasStringShader() {
        ClassNode shader = read(GL_SHADER);
        if (shader == null) return true;
        for (MethodNode method : shader.methods) {
            if (method.name.equals("<init>") && method.desc.endsWith(STRING_END)) return true;
        }
        return false;
    }

    private static ClassNode read(String internalName) {
        try (InputStream in = VeilShaderFix.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            if (in == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            return node;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
