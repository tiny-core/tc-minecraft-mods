package org.tinycore.core.grid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link ItemListing}: busca e ordem da grade do Terminal do Armazém. */
class ItemListingTest {

    private record Entry(String name, String mod, long amount) {}

    private static final Entry BREAD = new Entry("Bread", "minecraft", 64);
    private static final Entry COAL = new Entry("Coal", "minecraft", 128);
    private static final Entry HAMMER = new Entry("Builder Hammer", "minecolonies", 1);
    private static final List<Entry> ALL = List.of(BREAD, COAL, HAMMER);

    private static List<Entry> list(String query, ItemListing.Sort sort) {
        return ItemListing.filterAndSort(ALL, query, sort, Entry::name, Entry::mod, Entry::amount);
    }

    @Test
    void emptySearchShowsAllByAmount() {
        assertEquals(List.of(COAL, BREAD, HAMMER), list("", ItemListing.Sort.AMOUNT));
    }

    @Test
    void sortByNameIsAlphabeticalIgnoringCase() {
        assertEquals(List.of(BREAD, HAMMER, COAL), list("  ", ItemListing.Sort.NAME));
    }

    @Test
    void searchMatchesPartOfTheNameIgnoringCase() {
        assertEquals(List.of(COAL), list("CO", ItemListing.Sort.AMOUNT));
    }

    @Test
    void atSearchesByMod() {
        assertEquals(List.of(HAMMER), list("@minecol", ItemListing.Sort.AMOUNT));
        assertEquals(List.of(COAL, BREAD), list("@minecraft", ItemListing.Sort.AMOUNT));
    }

    private static List<String> tagsOf(Entry entry) {
        return entry == COAL ? List.of("minecraft:coals", "c:fuels") : entry == BREAD ? List.of("c:foods/bread") : List.of();
    }

    private static List<Entry> byTag(String query) {
        return ItemListing.filterAndSort(ALL, query, ItemListing.Sort.AMOUNT, Entry::name, Entry::mod,
                ItemListingTest::tagsOf, Entry::amount);
    }

    @Test
    void hashSearchesByTag() {
        assertEquals(List.of(COAL), byTag("#fuel"));
        assertEquals(List.of(BREAD), byTag("#C:FOODS"));
        assertEquals(List.of(), byTag("#ingots"));
    }

    @Test
    void bareHashShowsAll() {
        assertEquals(List.of(COAL, BREAD, HAMMER), byTag("#"));
    }

    @Test
    void doesNotChangeTheInputList() {
        List<Entry> input = new java.util.ArrayList<>(ALL);
        ItemListing.filterAndSort(input, "", ItemListing.Sort.NAME, Entry::name, Entry::mod, Entry::amount);
        assertEquals(ALL, input);
    }
}
