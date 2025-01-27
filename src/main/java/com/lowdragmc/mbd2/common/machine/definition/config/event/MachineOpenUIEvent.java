package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.config.event.graphprocess.GraphParameterGet;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;

import java.util.Map;
import java.util.Optional;


@Getter
@Cancelable
@LDLRegister(name = "MachineOpenUIEvent", group = "MachineEvent")
public class MachineOpenUIEvent extends MachineEvent {
    @GraphParameterGet
    public final Player player;

    public MachineOpenUIEvent(MBDMachine machine, Player player) {
        super(machine);
        this.player = player;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("player")).ifPresent(p -> p.setValue(player));
    }
}
