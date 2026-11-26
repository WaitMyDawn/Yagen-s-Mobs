package yagen.waitmydawn.yagens_mobs.guide.client;

import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.HolderLookup;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

public final class GuideTemplate implements ICustomComponent {
    private String species;
    private int offset, page;
    private List<GuidePlates.Card> cards = List.of();

    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup, HolderLookup.Provider registries) {
        species = lookup.apply(IVariable.wrap("#species", registries)).asString();
        offset = lookup.apply(IVariable.wrap("#offset", registries)).asNumber().intValue();
        if (species.equals("catalog")) cards = GuidePlates.cards(offset);
    }

    @Override
    public void build(int x, int y, int page) {
        this.page = page;
    }

    @Override
    public void onDisplayed(IComponentRenderContext context) {
        for (var card : cards) {
            // Patchouli positions widgets relative to the top of the cover and renders page content below its margin.
            var button = new Button(card.x(), card.y()+18, card.w(), card.h(), GuidePlates.name(card.species()),
                    b -> context.navigateToEntry(GuidePlates.id(card.species()), 0, true), supplier -> supplier.get()) {
                @Override protected void renderContents(GuiGraphics g, int mx, int my, float partial) {
                    if (isFocused()) g.renderOutline(getX(),getY(),width,height,GuidePlates.ACCENT);
                }
            };
            context.addWidget(button,page);
        }
    }

    @Override
    public void render(GuiGraphics graphics, IComponentRenderContext context, float partial, int mx, int my) {
        if (!species.equals("catalog")) {
            GuidePlates.sightings(graphics, species, offset);
            return;
        }
        GuidePlates.catalogFooter(graphics, offset);
        for (var card : cards) {
            boolean hovered = context.isAreaHovered(mx,my,card.x(),card.y(),card.w(),card.h());
            GuidePlates.card(graphics,card,hovered);
        }
    }
}
