package pharmacie.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.validation.ConstraintViolationException;
import pharmacie.dao.CommandeRepository;
import pharmacie.dao.LigneRepository;
import pharmacie.dao.MedicamentRepository;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
// Ce test est basé sur le jeu de données dans "test_data.sql"
class CommandeServiceTest {
    // Constantes pour les données de test
    private static final String ID_GROS_CLIENT = "2COM";
    private static final int COMMANDE_EN_COURS = 99998; // Commande non envoyée
    private static final int COMMANDE_ENVOYEE = 99999; // Commande déjà envoyée
    private static final int MEDICAMENT_DISPONIBLE = 93; // Disponible, stock=100, commandées=0
    private static final int MEDICAMENT_INDISPONIBLE = 97; // Indisponible
    private static final int MEDICAMENT_EN_COMMANDE = 98; // stock=26, commandées=20

    @Autowired
    private CommandeService service;

    @Autowired
    private CommandeRepository commandeDao;

    @Autowired
    private LigneRepository ligneDao;

    @Autowired
    private MedicamentRepository medicamentDao;

    // ========== Tests pour ajouterLigne() ==========

    @Test
    void testAjouterLigneNormale() {
        // Ajouter une ligne avec un médicament disponible
        var ligne = service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE, 10);

        assertNotNull(ligne);
        assertNotNull(ligne.getId());
        assertEquals(10, ligne.getQuantite());

        // Vérifier que les unités commandées ont été incrémentées
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        assertEquals(10, medicament.getUnitesCommandees());
    }

    @Test
    void testAjouterLigneMedicamentDejaPresent() {
        // Le médicament 98 est déjà dans la commande 99998 avec quantité 16
        var medicament = medicamentDao.findById(MEDICAMENT_EN_COMMANDE).orElseThrow();
        int unitesCommandeesAvant = medicament.getUnitesCommandees();

        // Ajouter 5 unités supplémentaires
        var ligne = service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_EN_COMMANDE, 5);

        // La quantité doit être 16 + 5 = 21
        assertEquals(21, ligne.getQuantite());

        // Vérifier que les unités commandées ont été incrémentées de 5
        medicament = medicamentDao.findById(MEDICAMENT_EN_COMMANDE).orElseThrow();
        assertEquals(unitesCommandeesAvant + 5, medicament.getUnitesCommandees());
    }

    @Test
    void testAjouterLigneMedicamentIndisponible() {
        // Tenter d'ajouter un médicament indisponible
        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_INDISPONIBLE, 10);
        }, "Ne doit pas pouvoir ajouter un médicament indisponible");
    }

    @Test
    void testAjouterLigneCommandeEnvoyee() {
        // Tenter d'ajouter une ligne à une commande déjà envoyée
        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(COMMANDE_ENVOYEE, MEDICAMENT_DISPONIBLE, 10);
        }, "Ne doit pas pouvoir ajouter une ligne à une commande déjà envoyée");
    }

    @Test
    void testAjouterLigneStockInsuffisant() {
        // Le médicament 93 a 100 unités en stock
        // Tenter de commander 101 unités
        assertThrows(IllegalStateException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE, 101);
        }, "Ne doit pas pouvoir commander plus que le stock disponible");
    }

    @Test
    void testAjouterLigneQuantiteNegative() {
        // Tenter d'ajouter une quantité négative (violation de @Positive)
        assertThrows(ConstraintViolationException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE, -5);
        }, "Ne doit pas accepter une quantité négative");
    }

    @Test
    void testAjouterLigneCommandeInexistante() {
        // Tenter d'ajouter une ligne à une commande inexistante
        assertThrows(NoSuchElementException.class, () -> {
            service.ajouterLigne(99999999, MEDICAMENT_DISPONIBLE, 10);
        }, "Ne doit pas accepter une commande inexistante");
    }

    @Test
    void testAjouterLigneMedicamentInexistant() {
        // Tenter d'ajouter un médicament inexistant
        assertThrows(NoSuchElementException.class, () -> {
            service.ajouterLigne(COMMANDE_EN_COURS, 99999999, 10);
        }, "Ne doit pas accepter un médicament inexistant");
    }

    // ========== Tests pour supprimerLigne() ==========

    @Test
    void testSupprimerLigneNormale() {
        // Ajouter une ligne d'abord
        var ligne = service.ajouterLigne(COMMANDE_EN_COURS, MEDICAMENT_DISPONIBLE, 15);
        int ligneId = ligne.getId();

        // Vérifier que le médicament a des unités commandées
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        int unitesCommandeesAvant = medicament.getUnitesCommandees();
        assertTrue(unitesCommandeesAvant > 0);

        // Supprimer la ligne
        service.supprimerLigne(ligneId);

        // Vérifier que la ligne n'existe plus
        assertFalse(ligneDao.findById(ligneId).isPresent());

        // Vérifier que les unités commandées ont été décrémentées
        medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        assertEquals(unitesCommandeesAvant - 15, medicament.getUnitesCommandees());
    }

    @Test
    void testSupprimerLigneCommandeEnvoyee() {
        // La commande 99999 a déjà été envoyée et contient des lignes
        var lignes = ligneDao.findByCommandeNumero(COMMANDE_ENVOYEE);
        assertFalse(lignes.isEmpty(), "La commande envoyée doit avoir des lignes");

        int ligneId = lignes.get(0).getId();

        // Tenter de supprimer une ligne d'une commande déjà envoyée
        assertThrows(IllegalStateException.class, () -> {
            service.supprimerLigne(ligneId);
        }, "Ne doit pas pouvoir supprimer une ligne d'une commande déjà envoyée");
    }

    @Test
    void testSupprimerLigneInexistante() {
        // Tenter de supprimer une ligne inexistante
        assertThrows(NoSuchElementException.class, () -> {
            service.supprimerLigne(99999999);
        }, "Ne doit pas accepter une ligne inexistante");
    }

    // ========== Tests pour enregistreExpedition() ==========

    @Test
    void testEnregistrerExpeditionNormale() {
        // Créer une nouvelle commande avec des lignes pour tester l'expédition
        var commande = service.creerCommande(ID_GROS_CLIENT);
        int commandeNum = commande.getNumero();

        // Ajouter quelques lignes
        service.ajouterLigne(commandeNum, MEDICAMENT_DISPONIBLE, 10);
        service.ajouterLigne(commandeNum, 94, 5);

        // Récupérer les stocks avant expédition
        var med93 = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        var med94 = medicamentDao.findById(94).orElseThrow();

        int stock93Avant = med93.getUnitesEnStock();
        int commandees93Avant = med93.getUnitesCommandees();
        int stock94Avant = med94.getUnitesEnStock();
        int commandees94Avant = med94.getUnitesCommandees();

        // Enregistrer l'expédition
        var commandeExpediee = service.enregistreExpedition(commandeNum);

        // Vérifier que la date d'envoi est renseignée
        assertNotNull(commandeExpediee.getEnvoyeele());

        // Vérifier que les stocks ont été décrémentés
        med93 = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        med94 = medicamentDao.findById(94).orElseThrow();

        assertEquals(stock93Avant - 10, med93.getUnitesEnStock());
        assertEquals(commandees93Avant - 10, med93.getUnitesCommandees());
        assertEquals(stock94Avant - 5, med94.getUnitesEnStock());
        assertEquals(commandees94Avant - 5, med94.getUnitesCommandees());
    }

    @Test
    void testEnregistrerExpeditionCommandeDejaEnvoyee() {
        // Tenter d'expédier une commande déjà envoyée
        assertThrows(IllegalStateException.class, () -> {
            service.enregistreExpedition(COMMANDE_ENVOYEE);
        }, "Ne doit pas pouvoir expédier une commande déjà envoyée");
    }

    @Test
    void testEnregistrerExpeditionCommandeInexistante() {
        // Tenter d'expédier une commande inexistante
        assertThrows(NoSuchElementException.class, () -> {
            service.enregistreExpedition(99999999);
        }, "Ne doit pas accepter une commande inexistante");
    }

    @Test
    void testEnregistrerExpeditionCommandeVide() {
        // Créer une commande sans lignes
        var commande = service.creerCommande(ID_GROS_CLIENT);
        int commandeNum = commande.getNumero();

        // Enregistrer l'expédition (doit fonctionner même sans lignes)
        var commandeExpediee = service.enregistreExpedition(commandeNum);

        // Vérifier que la date d'envoi est renseignée
        assertNotNull(commandeExpediee.getEnvoyeele());
    }

    @Test
    void testContrainteStockApresExpedition() {
        // Créer une commande avec un médicament et l'expédier
        var commande = service.creerCommande(ID_GROS_CLIENT);
        int commandeNum = commande.getNumero();

        // Ajouter une ligne
        service.ajouterLigne(commandeNum, MEDICAMENT_DISPONIBLE, 10);

        // Expédier la commande
        service.enregistreExpedition(commandeNum);

        // Vérifier que stock >= commandées
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        assertTrue(medicament.getUnitesEnStock() >= medicament.getUnitesCommandees(),
                "Le stock doit toujours être >= aux unités commandées");
    }
}
