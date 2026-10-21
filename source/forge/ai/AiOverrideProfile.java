package forge.ai;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import forge.game.card.Card;

/**
 * NeoForge AI override profile: an opt-in, runtime set of per-card AI behaviour overrides that leaves
 * upstream Forge card scripts, preset decks and default behaviour completely untouched.
 *
 * <p>Selected by name, either from the {@code neoforge.aiProfile} system property
 * ({@code -Dneoforge.aiProfile=abzan-armor}) or from the {@code sim} {@code --ai-profile=<name>} switch.
 * The profile is a small properties file under {@code res/ai/overrides/<name>.properties}:
 *
 * <pre>
 * deckDir=res/adventure/Realm of Legends/decks/starter/commanderprecon
 * decks=TDC - Abzan Armor.dck
 * ignoreRemoveDeckDuringPlay=Tower Defense
 * aiLogic=Slaughter the Strong=SlaughterTheStrong
 * hybridFilterMana=Twilight Mire,Fetid Heath
 * </pre>
 *
 * <p>{@code ignoreRemoveDeckDuringPlay} lists cards whose {@code AI:RemoveDeck:All} script hint should
 * NOT remove them from the AI's in-game play list (the hint is honoured for deck building as before).
 * A card that is not listed keeps upstream behaviour exactly.</p>
 *
 * <p>{@code aiLogic} lists {@code <card>=<logic>} pairs that force an AILogic for a card, overriding the
 * value in its untouched script. Cards not listed keep the scripted logic.</p>
 *
 * <p>{@code hybridFilterMana} lists cards whose {@code {X/Y}, {T}} mana ability the AI may pay for even
 * though the cost is not generic mana. Not listed, that ability is invisible to the AI as before.</p>
 */
public final class AiOverrideProfile {
    /** System property selecting a profile, e.g. {@code -Dneoforge.aiProfile=abzan-armor}. */
    public static final String SYSTEM_PROPERTY = "neoforge.aiProfile";

    private static final String SUBDIR = "overrides";

    private static AiOverrideProfile active = null;

    private final String name;
    private final String deckDir;
    private final List<String> decks;
    private final Set<String> ignoreRemoveDeckDuringPlay;
    private final Set<String> hybridFilterMana;
    private final Set<String> oneForOneManaFilters;
    private final boolean landManaAuras;
    private final Map<String, String> aiLogic;

    private AiOverrideProfile(String name, String deckDir, List<String> decks, Set<String> ignoreRemoveDeckDuringPlay,
            Set<String> hybridFilterMana, Set<String> oneForOneManaFilters, Map<String, String> aiLogic, boolean landManaAuras) {
        this.name = name;
        this.deckDir = deckDir;
        this.decks = decks;
        this.ignoreRemoveDeckDuringPlay = ignoreRemoveDeckDuringPlay;
        this.hybridFilterMana = hybridFilterMana;
        this.oneForOneManaFilters = oneForOneManaFilters;
        this.landManaAuras = landManaAuras;
        this.aiLogic = aiLogic;
    }

    /** The active profile (never null; an inactive profile overrides nothing). */
    public static synchronized AiOverrideProfile get() {
        if (active == null) {
            final String fromProperty = System.getProperty(SYSTEM_PROPERTY, "").trim();
            active = fromProperty.isEmpty() ? empty() : load(fromProperty);
        }
        return active;
    }

    /** Select a profile by name; a blank name deactivates overrides. */
    public static synchronized void setActive(String name) {
        active = (name == null || name.isBlank()) ? empty() : load(name.trim());
    }

    private static AiOverrideProfile empty() {
        return new AiOverrideProfile("", "", List.of(), Set.of(), Set.of(), Set.of(), Map.of(), false);
    }

    public static File profileFile(String name) {
        return new File(new File(AiProfileUtil.getAIProfileDir(), SUBDIR), name + ".properties");
    }

    /**
     * Split a profile list on commas. Double-quoted entries may contain commas, e.g.
     * {@code ignoreRemoveDeckDuringPlay="Selvala, Explorer Returned"}. Quotes are removed;
     * unquoted lists keep the original comma-separated behaviour.
     */
    private static List<String> splitList(final String value) {
        final List<String> result = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        result.add(current.toString());
        return result;
    }

    private static AiOverrideProfile load(String name) {
        final File file = profileFile(name);
        if (!file.isFile()) {
            System.err.println("[AiOverrideProfile] profile not found, no overrides active: " + file.getAbsolutePath());
            return empty();
        }
        final Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (final java.io.IOException e) {
            System.err.println("[AiOverrideProfile] could not read " + file.getAbsolutePath() + ": " + e.getMessage());
            return empty();
        }

        final Set<String> ignore = new LinkedHashSet<>();
        for (final String s : splitList(props.getProperty("ignoreRemoveDeckDuringPlay", ""))) {
            if (!s.isBlank()) { ignore.add(s.trim()); }
        }
        final List<String> decks = new ArrayList<>();
        for (final String s : splitList(props.getProperty("decks", ""))) {
            if (!s.isBlank()) { decks.add(s.trim()); }
        }
        final Map<String, String> aiLogic = new LinkedHashMap<>();
        for (final String entry : splitList(props.getProperty("aiLogic", ""))) {
            if (entry.isBlank()) { continue; }
            final int eq = entry.indexOf('=');
            if (eq <= 0) {
                System.err.println("[AiOverrideProfile] ignoring malformed aiLogic entry (expected '<card>=<logic>'): " + entry);
                continue;
            }
            aiLogic.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
        }
        final Set<String> hybridFilter = new LinkedHashSet<>();
        for (final String s : splitList(props.getProperty("hybridFilterMana", ""))) {
            if (!s.isBlank()) { hybridFilter.add(s.trim()); }
        }
        final Set<String> oneForOne = new LinkedHashSet<>();
        for (final String s : splitList(props.getProperty("oneForOneManaFilters", ""))) {
            if (!s.isBlank()) { oneForOne.add(s.trim()); }
        }
        return new AiOverrideProfile(name, props.getProperty("deckDir", "").trim(), List.copyOf(decks),
                Set.copyOf(ignore), Set.copyOf(hybridFilter), Set.copyOf(oneForOne), Map.copyOf(aiLogic),
                Boolean.parseBoolean(props.getProperty("landManaAuras", "false")));
    }

    public boolean isActive() {
        return !name.isEmpty();
    }

    public String getName() {
        return name;
    }

    /** Directory holding the profile's decks, or "" to keep the caller's default. */
    public String getDeckDir() {
        return deckDir;
    }

    /** Decks named by the profile, in seat order; empty to keep the caller's own decks. */
    public List<String> getDecks() {
        return decks;
    }

    /** True when this card's in-game play should NOT be blocked by its AI:RemoveDeck hint. */
    public boolean ignoreRemoveDeckDuringPlay(Card card) {
        return card != null && (ignoreRemoveDeckDuringPlay.contains(card.getName())
                || landManaAuras && ManaAuraAi.supportsBonusAura(card));
    }

    /** Enable only the structurally recognized land-Aura mana family, independently of other profiles. */
    public boolean supportsLandManaAuras() {
        return landManaAuras;
    }

    /**
     * An AILogic value the profile forces for this card, overriding the one in its script, or "" to keep
     * the scripted value. Consulted by the card-choice AI so a profile can opt a card into new logic
     * without editing the upstream script.
     */
    public String getAiLogic(Card card) {
        return card == null ? "" : aiLogic.getOrDefault(card.getName(), "");
    }

    /**
     * True when the profile opts this card's mana ability into hybrid filter handling: a mana ability
     * whose only cost is one pure-hybrid symbol (e.g. the {@code {B/G}} of the Shadowmoor/Eventide filter
     * lands) is treated as a payable net-positive filter, which the default AI refuses.
     */
    public boolean allowHybridFilterMana(Card card) {
        return card != null && hybridFilterMana.contains(card.getName());
    }

    /** Admit the structurally checked {1}, {T}: Add one mana of any color mechanic only for listed cards. */
    public boolean allowOneForOneManaFilter(Card card) {
        return card != null && oneForOneManaFilters.contains(card.getName());
    }
}
