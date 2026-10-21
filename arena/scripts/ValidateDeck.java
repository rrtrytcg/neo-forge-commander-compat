import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.deck.io.DeckSerializer;
import forge.item.PaperCard;
import java.io.File;
import java.util.Map;

class ValidateDeck {
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());
        FModel.initialize(null, null);
        for (String filename : args) {
            Deck deck = DeckSerializer.fromFile(new File(filename));
            int main = deck.getMain().countAll();
            int commander = deck.get(DeckSection.Commander).countAll();
            String problem = DeckFormat.Commander.getDeckConformanceProblem(deck);
            System.out.println("DECK " + filename + " main=" + main + " commander=" + commander + " legality=" + problem);
            if (main != 99 || commander != 1 || problem != null) throw new IllegalStateException("Deck validation failed");
            for (Map.Entry<PaperCard, Integer> entry : deck.getAllCardsInASinglePool(true, false)) {
                if (!entry.getKey().getEdition().equals("C15")) throw new IllegalStateException("Printing changed: " + entry.getKey());
                System.out.println("CARD " + entry.getValue() + " " + entry.getKey().getName() + " | " + entry.getKey().getEdition());
            }
        }
        System.out.println("VALIDATION PASS: recognized 1 commander + 99 main; Commander conformance including singleton and color identity; C15 printings");
        System.exit(0);
    }
}
