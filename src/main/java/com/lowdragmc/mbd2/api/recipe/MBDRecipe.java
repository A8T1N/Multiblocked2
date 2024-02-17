package com.lowdragmc.mbd2.api.recipe;

import com.google.common.collect.Table;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.capability.recipe.*;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import lombok.Getter;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author KilaBash
 * @date 2023/2/20
 * @implNote MBDRecipe
 */
@SuppressWarnings({"ConstantValue", "rawtypes", "unchecked"})
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class MBDRecipe implements net.minecraft.world.item.crafting.Recipe<Container> {
    public final MBDRecipeType recipeType;
    public final ResourceLocation id;
    public final Map<RecipeCapability<?>, List<Content>> inputs;
    public final Map<RecipeCapability<?>, List<Content>> outputs;
    public final Map<RecipeCapability<?>, List<Content>> tickInputs;
    public final Map<RecipeCapability<?>, List<Content>> tickOutputs;
    public final List<RecipeCondition> conditions;
    public CompoundTag data;
    public int duration;
    @Getter
    public boolean isFuel;

    public MBDRecipe(MBDRecipeType recipeType, ResourceLocation id, Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, Map<RecipeCapability<?>, List<Content>> tickInputs, Map<RecipeCapability<?>, List<Content>> tickOutputs, List<RecipeCondition> conditions, CompoundTag data, int duration, boolean isFuel) {
        this.recipeType = recipeType;
        this.id = id;
        this.inputs = inputs;
        this.outputs = outputs;
        this.tickInputs = tickInputs;
        this.tickOutputs = tickOutputs;
        this.conditions = conditions;
        this.data = data;
        this.duration = duration;
        this.isFuel = isFuel;
    }

    public Map<RecipeCapability<?>, List<Content>> copyContents(Map<RecipeCapability<?>, List<Content>> contents, @Nullable ContentModifier modifier) {
        Map<RecipeCapability<?>, List<Content>> copyContents = new HashMap<>();
        for (var entry : contents.entrySet()) {
            var contentList = entry.getValue();
            var cap = entry.getKey();
            if (contentList != null && !contentList.isEmpty()) {
                List<Content> contentsCopy = new ArrayList<>();
                for (Content content : contentList) {
                    contentsCopy.add(content.copy(cap, modifier));
                }
                copyContents.put(entry.getKey(), contentsCopy);
            }
        }
        return copyContents;
    }

    public MBDRecipe copy() {
        return new MBDRecipe(recipeType, id, copyContents(inputs, null), copyContents(outputs, null), copyContents(tickInputs, null), copyContents(tickOutputs, null), conditions, data, duration, isFuel);
    }

    public MBDRecipe copy(ContentModifier modifier) {
        return copy(modifier, true);
    }

    public MBDRecipe copy(ContentModifier modifier, boolean modifyDuration) {
        var copied = new MBDRecipe(recipeType, id, copyContents(inputs, modifier), copyContents(outputs, modifier), copyContents(tickInputs, modifier), copyContents(tickOutputs, modifier), conditions, data, duration, isFuel);
        if (modifyDuration) {
            copied.duration = modifier.apply(this.duration).intValue();
        }
        return copied;
    }

    @Override
    public @NotNull ResourceLocation getId() {
        return id;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return MBDRecipeSerializer.SERIALIZER;
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return recipeType;
    }

    @Override
    public boolean matches(@NotNull Container pContainer, @NotNull Level pLevel) {
        return false;
    }

    @Override
    public ItemStack assemble(Container inventory, RegistryAccess registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryManager) {
        return ItemStack.EMPTY;
    }

    ///////////////////////////////////////////////////////////////
    // **********************Internal Logic********************* //
    ///////////////////////////////////////////////////////////////

    public List<Content> getInputContents(RecipeCapability<?> capability) {
        return inputs.getOrDefault(capability, Collections.emptyList());
    }

    public List<Content> getOutputContents(RecipeCapability<?> capability) {
        return outputs.getOrDefault(capability, Collections.emptyList());
    }

    public List<Content> getTickInputContents(RecipeCapability<?> capability) {
        return tickInputs.getOrDefault(capability, Collections.emptyList());
    }

    public List<Content> getTickOutputContents(RecipeCapability<?> capability) {
        return tickOutputs.getOrDefault(capability, Collections.emptyList());
    }

    public ActionResult matchRecipe(IRecipeCapabilityHolder holder) {
        if (!holder.hasProxies()) return ActionResult.FAIL_NO_REASON;
        var result = matchRecipe(IO.IN, holder, inputs, false);
        if (!result.isSuccess()) return result;
        result = matchRecipe(IO.OUT, holder, outputs, false);
        if (!result.isSuccess()) return result;
        return ActionResult.SUCCESS;
    }

    public ActionResult matchTickRecipe(IRecipeCapabilityHolder holder) {
        if (hasTick()) {
            if (!holder.hasProxies()) return ActionResult.FAIL_NO_REASON;
            var result = matchRecipe(IO.IN, holder, tickInputs, false);
            if (!result.isSuccess()) return result;
            result = matchRecipe(IO.OUT, holder, tickOutputs, false);
            if (!result.isSuccess()) return result;
        }
        return ActionResult.SUCCESS;
    }

    public ActionResult matchRecipe(IO io, IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Content>> contents, boolean calculateExpectingRate) {
        Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> capabilityProxies = holder.getCapabilitiesProxy();
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : contents.entrySet()) {
            Set<IRecipeHandler<?>> used = new HashSet<>();
            List content = new ArrayList<>();
            Map<String, List> contentSlot = new HashMap<>();
            for (Content cont : entry.getValue()) {
                if (cont.slotName == null) {
                    content.add(cont.content);
                } else {
                    contentSlot.computeIfAbsent(cont.slotName, s -> new ArrayList<>()).add(cont.content);
                }
            }
            RecipeCapability<?> capability = entry.getKey();
            content = content.stream().map(capability::copyContent).toList();
            if (content.isEmpty() && contentSlot.isEmpty()) continue;
            if (content.isEmpty()) content = null;

            var result = handlerContentsInternal(io, io, capabilityProxies, capability, used, content, contentSlot, content, contentSlot, true);
            if (result.getA() == null && result.getB().isEmpty()) continue;
            result = handlerContentsInternal(IO.BOTH, io, capabilityProxies, capability, used, result.getA(), result.getB(), content, contentSlot, true);

            if (result.getA() != null || !result.getB().isEmpty()) {
                var expectingRate = 0f;
                // TODO calculateExpectingRate
//                if (calculateExpectingRate) {
//                    if (result.getA() != null) {
//                        expectingRate = Math.max(capability.calculateAmount(result.getA()), expectingRate);
//                    }
//                    if (!result.getB().isEmpty()) {
//                        for (var c : result.getB().values()) {
//                            expectingRate = Math.max(capability.calculateAmount(c), expectingRate);
//                        }
//                    }
//                }
                if (io == IO.IN) {
                    return ActionResult.fail(() -> Component.translatable("mbd2.recipe_logic.insufficient_in").append(": ").append(capability.getTraslateComponent()), expectingRate);
                } else if (io == IO.OUT) {
                    return ActionResult.fail(() -> Component.translatable("mbd2.recipe_logic.insufficient_out").append(": ").append(capability.getTraslateComponent()), expectingRate);
                } else {
                    return ActionResult.FAIL_NO_REASON;
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    public boolean handleTickRecipeIO(IO io, IRecipeCapabilityHolder holder) {
        if (!holder.hasProxies() || io == IO.BOTH) return false;
        return handleRecipe(io, holder, io == IO.IN ? tickInputs : tickOutputs);
    }

    public boolean handleRecipeIO(IO io, IRecipeCapabilityHolder holder) {
        if (!holder.hasProxies() || io == IO.BOTH) return false;
        return handleRecipe(io, holder, io == IO.IN ? inputs : outputs);
    }

    public boolean handleRecipe(IO io, IRecipeCapabilityHolder holder, Map<RecipeCapability<?>, List<Content>> contents) {
        Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> capabilityProxies = holder.getCapabilitiesProxy();
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : contents.entrySet()) {
            Set<IRecipeHandler<?>> used = new HashSet<>();
            List content = new ArrayList<>();
            Map<String, List> contentSlot = new HashMap<>();
            List contentSearch = new ArrayList<>();
            Map<String, List> contentSlotSearch = new HashMap<>();
            for (Content cont : entry.getValue()) {
                if (cont.slotName == null) {
                    contentSearch.add(cont.content);
                } else {
                    contentSlotSearch.computeIfAbsent(cont.slotName, s -> new ArrayList<>()).add(cont.content);
                }
                if (cont.chance >= 1 || MBD2.RND.nextFloat() < (cont.chance + holder.getChanceTier() * cont.tierChanceBoost)) { // chance input
                    if (cont.slotName == null) {
                        content.add(cont.content);
                    } else {
                        contentSlot.computeIfAbsent(cont.slotName, s -> new ArrayList<>()).add(cont.content);
                    }
                }
            }
            RecipeCapability<?> capability = entry.getKey();
            content = content.stream().map(capability::copyContent).toList();
            if (content.isEmpty() && contentSlot.isEmpty()) continue;
            if (content.isEmpty()) content = null;

            var result = handlerContentsInternal(io, io, capabilityProxies, capability, used, content, contentSlot, contentSearch, contentSlotSearch, false);
            if (result.getA() == null && result.getB().isEmpty()) continue;
            result = handlerContentsInternal(IO.BOTH, io, capabilityProxies, capability, used, result.getA(), result.getB(), contentSearch, contentSlotSearch, false);

            if (result.getA() != null || !result.getB().isEmpty()) {
                MBD2.LOGGER.warn("io error while handling a recipe {} outputs. holder: {}", id, holder);
                return false;
            }
        }
        return true;
    }

    private Tuple<List, Map<String, List>> handlerContentsInternal(
            IO capIO, IO io, Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> capabilityProxies,
            RecipeCapability<?> capability, Set<IRecipeHandler<?>> used,
            List content, Map<String, List> contentSlot,
            List contentSearch, Map<String, List> contentSlotSearch,
            boolean simulate) {
        if (capabilityProxies.contains(capIO, capability)) {
            var handlers = capabilityProxies.get(capIO, capability);
            // handle distinct first
            for (IRecipeHandler<?> handler : handlers) {
                if (!handler.isDistinct()) continue;
                var slotNames = handler.getSlotNames();
                var result = handler.handleRecipe(io, this, contentSearch, null, true);
                if (result == null) {
                    // check distinct slot handler
                    if (slotNames.containsAll(contentSlotSearch.keySet())) {
                        boolean success = true;
                        for (var entry : contentSlotSearch.entrySet()) {
                            List<?> left = handler.handleRecipe(io, this, entry.getValue(), entry.getKey(), true);
                            if (left != null) {
                                success = false;
                                break;
                            }
                        }
                        if (success) {
                            if (!simulate) {
                                for (var entry : contentSlot.entrySet()) {
                                    handler.handleRecipe(io, this, entry.getValue(), entry.getKey(), false);
                                }
                            }
                            contentSlot.clear();
                        }
                    }
                    if (contentSlot.isEmpty()) {
                        if (!simulate) {
                            handler.handleRecipe(io, this, content, null, false);
                        }
                        content = null;
                    }
                }
                if (content == null && contentSlot.isEmpty()) {
                    break;
                }
            }
            if (content != null || !contentSlot.isEmpty()) {
                // handle undistinct later
                for (IRecipeHandler<?> proxy : handlers) {
                    if (used.contains(proxy) || proxy.isDistinct()) continue;
                    used.add(proxy);
                    if (content != null) {
                        content = proxy.handleRecipe(io, this, content, null, simulate);
                    }
                    var slotNames = proxy.getSlotNames();
                    if (!slotNames.isEmpty()) {
                        Iterator<String> iterator = contentSlot.keySet().iterator();
                        while (iterator.hasNext()) {
                            String key = iterator.next();
                            if (slotNames.contains(key)) {
                                List<?> left = proxy.handleRecipe(io, this, contentSlot.get(key), key, simulate);
                                if (left == null) iterator.remove();
                            }
                        }
                    }
                    if (content == null && contentSlot.isEmpty()) break;
                }
            }
        }
        return new Tuple<>(content, contentSlot);
    }

    public boolean hasTick() {
        return !tickInputs.isEmpty() || !tickOutputs.isEmpty();
    }

    public void preWorking(IRecipeCapabilityHolder holder) {
        handlePre(inputs, holder, IO.IN);
        handlePre(outputs, holder, IO.OUT);
    }

    public void postWorking(IRecipeCapabilityHolder holder) {
        handlePost(inputs, holder, IO.IN);
        handlePost(outputs, holder, IO.OUT);
    }

    public void handlePre(Map<RecipeCapability<?>, List<Content>> contents, IRecipeCapabilityHolder holder, IO io) {
        contents.forEach(((capability, tuples) -> {
            if (holder.getCapabilitiesProxy().contains(io, capability)) {
                for (IRecipeHandler<?> capabilityProxy : holder.getCapabilitiesProxy().get(io, capability)) {
                    capabilityProxy.preWorking(holder, io, this);
                }
            } else if (holder.getCapabilitiesProxy().contains(IO.BOTH, capability)) {
                for (IRecipeHandler<?> capabilityProxy : holder.getCapabilitiesProxy().get(IO.BOTH, capability)) {
                    capabilityProxy.preWorking(holder, io, this);
                }
            }
        }));
    }

    public void handlePost(Map<RecipeCapability<?>, List<Content>> contents, IRecipeCapabilityHolder holder, IO io) {
        contents.forEach(((capability, tuples) -> {
            if (holder.getCapabilitiesProxy().contains(io, capability)) {
                for (IRecipeHandler<?> capabilityProxy : holder.getCapabilitiesProxy().get(io, capability)) {
                    capabilityProxy.postWorking(holder, io, this);
                }
            } else if (holder.getCapabilitiesProxy().contains(IO.BOTH, capability)) {
                for (IRecipeHandler<?> capabilityProxy : holder.getCapabilitiesProxy().get(IO.BOTH, capability)) {
                    capabilityProxy.postWorking(holder, io, this);
                }
            }
        }));
    }

    public ActionResult checkConditions(@Nonnull RecipeLogic recipeLogic) {
        if (conditions.isEmpty()) return ActionResult.SUCCESS;
        Map<String, List<RecipeCondition>> or = new HashMap<>();
        for (RecipeCondition condition : conditions) {
            if (condition.isOr()) {
                or.computeIfAbsent(condition.getType(), type -> new ArrayList<>()).add(condition);
            } else if (condition.test(this, recipeLogic) == condition.isReverse()) {
                return ActionResult.fail(() -> Component.translatable("mbd2.recipe_logic.condition_fails").append(": ").append(condition.getTooltips()));
            }
        }
        for (List<RecipeCondition> conditions : or.values()) {
            if (conditions.stream().allMatch(condition -> condition.test(this, recipeLogic) == condition.isReverse())) {
                return ActionResult.fail(() -> Component.translatable("mbd2.recipe_logic.condition_fails"));
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     *
     * @param isSuccess is action success
     * @param reason if fail, fail reason
     * @param expectingRate if recipe matching fail, the expecting rate of one cap.
     *                    <br>
     *                    For example, recipe require 300eu and 10 apples, and left 100eu and 5 apples after recipe searching.
     *                    <br>
     *                    EU Missing Rate : 300 / (300 - 100) = 1.5
     *                    <br>
     *                    Item Missing Rate : 10 / (10 - 5) = 2
     *                    <br>
     *                    return max expecting rate --- 2
     */
    public static record ActionResult(boolean isSuccess, @Nullable Supplier<Component> reason, float expectingRate) {

        public final static ActionResult SUCCESS = new ActionResult(true, null, 0);
        public final static ActionResult FAIL_NO_REASON = new ActionResult(true, null, 0);

        public static ActionResult fail(@Nullable Supplier<Component> component) {
            return new ActionResult(false, component, 0);
        }

        public static ActionResult fail(@Nullable Supplier<Component> component, float expectingRate) {
            return new ActionResult(false, component, expectingRate);
        }
    }

}
