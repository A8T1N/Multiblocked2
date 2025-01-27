package com.lowdragmc.mbd2.integration.botania.trait;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.IntTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import vazkii.botania.api.mana.ManaPool;

import java.util.Optional;

public class CopiableManaPool implements ManaPool, ITagSerializable<IntTag>, IContentChangeAware {
    @Getter
    @Setter
    public Runnable onContentsChanged = () -> {};

    private final MBDMachine machine;
    @Getter
    protected final int maxMana;

    protected int mana;

    public CopiableManaPool(MBDMachine machine, int capacity) {
        this(machine, capacity, 0);
    }

    public CopiableManaPool(MBDMachine machine, int capacity, int mana) {
        this.machine = machine;
        this.maxMana = capacity;
        this.mana = mana;
    }

    public CopiableManaPool copy() {
        return new CopiableManaPool(machine, maxMana, mana);
    }

    @Override
    public Level getManaReceiverLevel() {
        return machine.getLevel();
    }

    @Override
    public BlockPos getManaReceiverPos() {
        return machine.getPos();
    }

    @Override
    public int getCurrentMana() {
        return mana;
    }


    @Override
    public boolean isFull() {
        return mana >= maxMana;
    }

    @Override
    public void receiveMana(int mana) {
        var old = this.mana;
        this.mana = Math.max(0, Math.min(this.mana + mana, maxMana));
        if (old != this.mana) onContentsChanged.run();
    }

    @Override
    public boolean canReceiveManaFromBursts() {
        return !isFull();
    }

    @Override
    public IntTag serializeNBT() {
        return IntTag.valueOf(mana);
    }

    @Override
    public void deserializeNBT(IntTag nbt) {
        mana = nbt.getAsInt();
    }

    @Override
    public boolean isOutputtingPower() {
        return false;
    }

    @Override
    public Optional<DyeColor> getColor() {
        return Optional.empty();
    }

    @Override
    public void setColor(Optional<DyeColor> color) {

    }
}
