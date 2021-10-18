package com.rubydesic.chatcalc.mixin;

import com.rubydesic.chatcalc.calculators.CalculatorService;
import com.rubydesic.chatcalc.calculators.impl.wolfram.WolframService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.TextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

	private final CompletableFuture<? extends CalculatorService> api = WolframService.createAsync();

	@Redirect(
		method = "keyPressed",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/ChatScreen;sendMessage(Ljava/lang/String;)V"
		)
	)
	public void onSendMessage(ChatScreen receiver, String message) {
		if (!message.startsWith("/eq ")) {
			receiver.sendMessage(message);
			return;
		}

		String query = message.substring(4);
		ChatComponent chat = Minecraft.getInstance().gui.getChat();

		chat.addRecentChat(message);
		chat.addMessage(new TextComponent("Solving: " + query));
		api.join().query(query).thenAccept(m -> {
			chat.addRecentChat(m);
			chat.addMessage(new TextComponent(m));
		});
	}
}
