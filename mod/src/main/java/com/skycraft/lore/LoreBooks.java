package com.skycraft.lore;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Every readable book and note. The server only needs the ids (and category/cover/weight/value); titles live in the
 * language file ({@code book.skycraft.<id>}) and the text in {@code assets/skycraft/lore/books/<id>.json}, which
 * the client loads when the book is opened.
 *
 * <p>{@code tools/textures/lore.py} parses the {@code book(...)} lines below to generate the loot table, so keep
 * one book per line in this exact form.</p>
 */
public final class LoreBooks {
    public enum Category {
        HISTORY, DAEDRA, DWEMER, DRAGONS, GUIDE, FICTION, POEM, LETTER, NOTE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("book.skycraft.category." + id());
        }

        /** Letters and notes are single loose sheets rather than bound books. */
        public boolean isSheet() {
            return this == LETTER || this == NOTE;
        }
    }

    /** Cover colors (model override index). */
    public static final int BROWN = 0, RED = 1, GREEN = 2, BLUE = 3, BLACK = 4, SHEET = 5, GOLD = 6;

    /**
     * @param weight loot weight (higher = more common)
     * @param value  gold value (stored on the stack as {@code skycraft_value})
     */
    public record Book(String id, Category category, int cover, int weight, int value) {
        public Component title() {
            return Component.translatable("book.skycraft." + id);
        }
    }

    private static final Map<String, Book> BOOKS = new LinkedHashMap<>();

    static {
        // history
        book("brief_history_nordic_empire", Category.HISTORY, RED, 10, 25);
        book("nine_holds", Category.HISTORY, BROWN, 10, 15);
        book("winter_without_end", Category.HISTORY, BLUE, 8, 20);
        book("stones_and_mileposts", Category.HISTORY, BROWN, 8, 10);
        // daedra
        book("wary_primer_daedra", Category.DAEDRA, BLACK, 6, 40);
        book("price_of_bargains", Category.DAEDRA, BLACK, 6, 30);
        book("shrine_keeper_confession", Category.DAEDRA, RED, 5, 35);
        // dwemer
        book("where_deep_folk_went", Category.DWEMER, GOLD, 6, 40);
        book("dwarven_mechanisms", Category.DWEMER, GOLD, 6, 45);
        book("brass_and_resonance", Category.DWEMER, BROWN, 5, 35);
        // dragons and the Voice
        book("when_dragons_ruled", Category.DRAGONS, RED, 7, 30);
        book("patience_of_the_voice", Category.DRAGONS, BLUE, 6, 35);
        book("on_the_dragon_blooded", Category.DRAGONS, GOLD, 5, 50);
        book("walls_that_speak", Category.DRAGONS, BROWN, 6, 30);
        // guides
        book("novice_alchemist", Category.GUIDE, GREEN, 10, 20);
        book("hammer_and_hearth", Category.GUIDE, BROWN, 10, 20);
        book("gentle_hands", Category.GUIDE, BLACK, 8, 25);
        book("on_standing_stones", Category.GUIDE, BLUE, 9, 20);
        book("northern_wilds", Category.GUIDE, GREEN, 10, 15);
        book("first_steps_spellcraft", Category.GUIDE, BLUE, 9, 25);
        book("huntsmans_almanac", Category.GUIDE, GREEN, 9, 15);
        // fiction
        book("cheese_wheel_dispute", Category.FICTION, RED, 9, 10);
        book("hearth_for_two", Category.FICTION, RED, 7, 15);
        book("retired_guard", Category.FICTION, BROWN, 9, 10);
        book("mudcrab_who_haggled", Category.FICTION, GREEN, 9, 5);
        book("skeever_king", Category.FICTION, BROWN, 8, 5);
        book("lantern_on_frostmoor", Category.FICTION, BLUE, 8, 15);
        // songs and poems
        book("song_of_hearthfires", Category.POEM, BROWN, 8, 10);
        book("ballad_iron_shield", Category.POEM, RED, 8, 10);
        book("in_praise_of_mead", Category.POEM, GREEN, 9, 5);
        book("lament_barrow_gate", Category.POEM, BLACK, 7, 10);
        // letters and notes
        book("letter_homesick_legionnaire", Category.LETTER, SHEET, 9, 5);
        book("note_camp_orders", Category.NOTE, SHEET, 9, 5);
        book("note_scrawled_warning", Category.NOTE, SHEET, 9, 5);
        book("note_shopping_list", Category.NOTE, SHEET, 9, 5);
        book("journal_lost_explorer", Category.NOTE, SHEET, 7, 10);
        book("note_treasure_hint", Category.NOTE, SHEET, 6, 10);
    }

    private LoreBooks() {}

    private static void book(String id, Category category, int cover, int weight, int value) {
        BOOKS.put(id, new Book(id, category, cover, weight, value));
    }

    @Nullable
    public static Book get(String id) {
        return id == null ? null : BOOKS.get(id);
    }

    public static List<Book> all() {
        return Collections.unmodifiableList(new ArrayList<>(BOOKS.values()));
    }

    /** Weighted random book among those matching {@code filter}. */
    @Nullable
    public static Book random(RandomSource random, Predicate<Book> filter) {
        int total = 0;
        for (Book b : BOOKS.values()) if (filter.test(b)) total += b.weight();
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (Book b : BOOKS.values()) {
            if (!filter.test(b)) continue;
            roll -= b.weight();
            if (roll < 0) return b;
        }
        return null;
    }
}
