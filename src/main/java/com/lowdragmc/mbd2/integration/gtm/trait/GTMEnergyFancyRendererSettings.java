package com.lowdragmc.mbd2.integration.gtm.trait;

import com.lowdragmc.lowdraglib.client.model.ModelFactory;
import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.client.utils.RenderBufferUtils;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberColor;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.utils.ColorUtils;
import com.lowdragmc.mbd2.api.capability.MBDCapabilities;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

public class GTMEnergyFancyRendererSettings implements IToggleConfigurable {
    private final GTMEnergyCapabilityTraitDefinition definition;
    @Getter
    @Setter
    @Persisted
    private boolean enable;

    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.fancy_renderer.color", tips = "config.definition.trait.fancy_renderer.color.tooltip")
    @NumberColor
    private int color = 0xaa00AA98;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.fancy_renderer.position", tips = "config.definition.trait.fancy_renderer.position.tooltip")
    @NumberRange(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
    private Vector3f position = new Vector3f(0, 0, 0);
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.fancy_renderer.rotation", tips = "config.definition.trait.fancy_renderer.rotation.tooltip")
    @NumberRange(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
    private Vector3f rotation = new Vector3f(0, 0, 0);
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.fancy_renderer.scale", tips = "config.definition.trait.fancy_renderer.scale.tooltip")
    @NumberRange(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
    private Vector3f scale = new Vector3f(1, 1, 1);
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.fancy_renderer.rotate_orientation", tips = "config.definition.trait.fancy_renderer.rotate_orientation.tooltip")
    private boolean rotateOrientation = true;
    @Getter
    @Setter
    @Configurable(name = "config.definition.trait.fancy_renderer.percent_height", tips = "config.definition.trait.fancy_renderer.percent_height.tooltip")
    private boolean percentHeight = false;

    // run-time;
    private IRenderer renderer;

    public GTMEnergyFancyRendererSettings(GTMEnergyCapabilityTraitDefinition definition) {
        this.definition = definition;
    }

    public IRenderer createRenderer() {
        if (isEnable()) {
            return renderer == null ? (renderer = new Renderer()) : renderer;
        } else return IRenderer.EMPTY;
    }

    private class Renderer implements IRenderer {
        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean hasTESR(BlockEntity blockEntity) {
            return true;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void render(BlockEntity blockEntity, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
            var optional = blockEntity.getCapability(MBDCapabilities.CAPABILITY_MACHINE).resolve();
            if (optional.isPresent() && optional.get() instanceof MBDMachine machine) {
                if (machine.getTraitByDefinition(definition) instanceof GTMEnergyCapabilityTrait trait) {
                    var storage = trait.container;
                    if (storage.getEnergyStored() == 0 || storage.getEnergyCapacity() == 0) return;

                    poseStack.pushPose();

                    // rotate orientation
                    if (rotateOrientation) {
                        poseStack.translate(0.5D, 0.5d, 0.5D);
                        poseStack.mulPose(ModelFactory.getQuaternion(machine.getFrontFacing().orElse(Direction.NORTH)));
                        poseStack.translate(-0.5D, -0.5d, -0.5D);
                    }

                    // transform
                    poseStack.translate(position.x, position.y, position.z);
                    poseStack.translate(0.5D, 0.5d, 0.5D);
                    // rotation
                    poseStack.mulPose(new Quaternionf().rotateXYZ((float) Math.toRadians(rotation.x), (float) Math.toRadians(rotation.y), (float) Math.toRadians(rotation.z)));
                    // scale
                    poseStack.scale(scale.x, scale.y, scale.z);
                    poseStack.translate(-0.5D, -0.5d, -0.5D);

                    RenderSystem.enableBlend();
                    RenderSystem.enableDepthTest();
                    RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                    var tessellator = Tesselator.getInstance();
                    var buffer = tessellator.getBuilder();
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

                    RenderBufferUtils.drawCubeFace(poseStack, buffer,
                            0, 0, 0, 1,
                            percentHeight ? storage.getEnergyStored() * 1f / storage.getEnergyCapacity() : 1, 1,
                            ColorUtils.red(color), ColorUtils.green(color), ColorUtils.blue(color), ColorUtils.alpha(color),
                            true);

                    tessellator.end();
                    poseStack.popPose();
                }
            }
        }

    }
}
