package com.skycraft.lore.client;

import com.skycraft.core.SkyData;
import com.skycraft.lore.LoreBookItem;
import com.skycraft.lore.LoreBooks;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/** Client entry points used from common code through {@code DistExecutor} (only class-loaded on the client). */
public final class LoreClient {
    private LoreClient() {}

    public static void openBook(String id) {
        LoreBooks.Book book = LoreBooks.get(id);
        if (book == null) return;
        Minecraft.getInstance().setScreen(new BookScreen(book));
    }

    public static boolean hasRead(String id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && LoreBookItem.hasRead(SkyData.get(mc.player), id);
    }

    @Nullable
    public static String author(String id) {
        BookTexts.Text text = BookTexts.get(id);
        return text == null ? null : text.author();
    }
}
