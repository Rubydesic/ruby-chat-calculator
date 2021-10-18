package com.rubydesic.chatcalc.fabric;

import com.rubydesic.chatcalc.ChatCalcMod;
import net.fabricmc.api.ModInitializer;

public class ChatCalcModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ChatCalcMod.init();
    }
}
