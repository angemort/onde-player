import fr.ondes.player.HeaderMotionRules;
public final class HeaderMotionRulesTest {
    private static void expect(boolean ok, String scenario) {
        if (!ok) throw new AssertionError(scenario);
    }
    public static void main(String[] args) {
        int show = HeaderMotionRules.SHOW, hide = HeaderMotionRules.HIDE,
                none = HeaderMotionRules.NONE;
        expect(HeaderMotionRules.intent(45,46)==none,"Micro-geste vers le haut");
        expect(HeaderMotionRules.intent(47,46)==show,"Retour du header partout dans la liste");
        expect(HeaderMotionRules.intent(-50,46)==hide,"Header masqué vers le bas");
        expect(HeaderMotionRules.intent(0,46)==none,"Aucun mouvement");
        float distance = 0f;
        distance = HeaderMotionRules.accumulate(distance,-25,115);
        distance = HeaderMotionRules.accumulate(distance,-28,115);
        expect(HeaderMotionRules.intent(distance,46)==hide,"Deux mouvements vers le bas");
        distance = HeaderMotionRules.accumulate(distance,20,115);
        expect(HeaderMotionRules.intent(distance,46)==none,"Inversion annule l'ancien geste");
        distance = HeaderMotionRules.accumulate(distance,48,115);
        expect(HeaderMotionRules.intent(distance,46)==show,"Une remontée affiche la barre");
        expect(HeaderMotionRules.accumulate(114,40,115)==115,"Distance bornée");
        expect(HeaderMotionRules.scrollIntent(90,46)==hide,"Inertie vers le bas masque le header");
        expect(HeaderMotionRules.scrollIntent(-90,46)==show,"Inertie vers le haut affiche le header");
        expect(HeaderMotionRules.scrollIntent(12,46)==none,"Petite inertie ignorée");
        System.out.println("HeaderMotionRules: OK");
    }
}
