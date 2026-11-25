package yagen.waitmydawn.yagens_mobs.entity.salvage;

public final class SalvageClient {
    private SalvageClient() {}

    public static void receive(SalvageRoutes.Snapshot snapshot) {
        if (snapshot.data() == null) {
            return;
        }
        switch (snapshot.data().getString("Mode")) {
            case "Overlay" -> SalvageLinkOverlay.accept(snapshot.data());
            case "Rename" -> SalvageNestScreen.renamed(snapshot.data());
            default -> SalvageRouteScreen.accept(snapshot);
        }
    }
}
