package com.rubydesic.chatcalc.forge;

import com.rubydesic.chatcalc.ChatCalcMod;
import net.minecraftforge.fml.common.Mod;

@Mod(ChatCalcMod.MOD_ID)
public class ChatCalcModForge {

    public ChatCalcModForge() {
        ChatCalcMod.init();
    }
}
