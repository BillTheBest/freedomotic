package it.freedom.X10;

import it.freedom.X10.gateways.PMix35Gateway;
import it.freedom.api.Sensor;
import it.freedom.api.events.GenericEvent;
import it.freedom.api.events.SwitchPressed;
import it.freedom.exceptions.UnableToExecuteException;
import it.freedom.util.Info;
import it.nicoletti.serial.SerialConnectionProvider;
import it.nicoletti.serial.SerialDataConsumer;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Vector;

public class X10Sensor extends Sensor implements SerialDataConsumer {

    Vector<String> address = new Vector<String>();
    private SerialConnectionProvider usb;
    /** Ack inviato dal PMix35. */
    private static final String PMIX_ACK = "$<9000!4A#                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 ".trim();
    /** Ack negativo inviato dal PMix35. */
    private static final String PMIX_NACK = "$<9000?4A#                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  ".trim();
    /** Messaggio vuoto inviato dal PMix35. */
    private static final String PMIX_NULL = "$<900029#".trim();

    public X10Sensor() {
        super("X10Sensor", "/it.nicoletti.x10/x10sensor.xml");
        usb = PMix35Gateway.getInstance();
        usb.addListener(this);
        usb.connect();
        setAsNotPollingSensor();
        start();
    }
    static long waitTime = 0;
    static String last = "";

    /**
     * Questa funzione, fintanto che il plugin è in esecuzione, aspetta messaggi
     * dal gateway e li combina per lanciare gli eventi.<br>
     * I messaggi possono essere indirizzi o comandi. I comandi si dividono in due
     * categorie quelli che necessitano un indirizo e quelli generali (vedi
     * boolean isCommandAddressed(String)). Come da specifiche il protocollo permette
     * l'invio di più iondirizzi sucessivi, come l'invio di più comandi successivi.
     * In questi casi tutti i dispositivi (relativi agli indirizzi inviati) dovranno
     * eseguire tutti i comandi inviati. Dato che gli eventi vengono lanciati per
     * coppie, questa funzione creerà le coppie indirizzo-comando. Per esempio:<br>
     * <table border=1>
     * <td><tr>Messaggi</tr><tr>Coppie</tr></td>
     * <td><tr>A01  AON            </tr><tr> A01-AON</tr></td>
     * <td><tr>A01  A02  A03  AON  </tr><tr> A01-AON   A02-AON   A03-AON</tr></td>
     * <td><tr>A01  AON  ABGT ABGT </tr><tr> A01-AON   A01-ABGT  A01-ABGT</tr></td>
     * </table>
     * <br>
     * Inoltre esistono dei comandi che non necessitano degli indirizzi come ad
     * esempio TutteLeLuciAccese. Questi comandi vengono inviati tramite eventi
     * contenenti solo il comando senza l'indirizzo.
     *
     * <br><br>
     * In dettagli questa funzione legge il messaggio, controlla se è valido (un
     * messaggio deve arrivare due volte), dopo di che lo elabora.<br>
     * Se è un indirizzo lo aggiunge alla lista degli indirizzi, però se è il
     * primo della serie, prima svuota la lista.<br>
     * Se è un comando controlla se è un comando da associare ad un indirizzo e
     * dunque crea le coppie con ogni indirizzo trovato nella lista, altrimenti
     * invia solo un evento contente il singolo comando senza bisogno di
     * associargli alcun indirizzo.<br>
     * Ogni volta che si passa da un comando ad un indirizzo, o da un comando con
     * indirizzo ad un comando senza indirizzo, la lista degli indirizzi viene
     * resettata.
     * @param readed 
     */
    public void onDataAvailable(String readed) {
        // Leggo il messaggio
        // (devo leggerne due uguali e successivi per prenderli in considerazione)
        String[] tokens = readed.split("#");
        for (int i = 0; i < tokens.length; i++) {
            String line = parseReaded(tokens[i] + "#");
            if (!line.equals("")) {
                Date time = new Date();
                System.out.println(time.toString() + " X10 Sensor receive message '" + line + "'");
                evaluateMessageSequence(line);
            }
        }
    }

    private void evaluateMessageSequence(String message) {
        boolean isAddressing = false;   // sono in fase di indirizzamento

        if (message.compareToIgnoreCase(last) != 0) {
            last = message;
            return;
        }
        // Se è un indirizzo
        if (isAddress(message)) {
            // Se è il primo indirizzo della serie
            if (!isAddressing) {
                resetAddress();
            }
            // Aggiungo il messaggio
            isAddressing = true;
            addAddress(message);
        }

        // Se è un comando
        if (isCommand(message)) {
            isAddressing = false;
            // Genero gli eventi
            sendEvents(message);
            // Se è un comando senza indirizzo
            if (!thisCommandNeedsAddres(message)) {
                resetAddress();
            }
        }

        last = "";
    }

    /**
     * Cancella la lista degli indirizzi. Questa lista viene cancellata ogni volta
     * che inzia una nuova sequenza di indirizzi, ovvero quando i messaggi passano
     * da un comando ad un indirizzo o dopo che è arrivato un comando che non ha
     * bisogno di un indirizzo.
     */
    private void resetAddress() {
        address.removeAllElements();
    }

    /**
     * Aggiunge un indirizzo alla lista degli indirizzi.
     * @param add indirizzo da aggiungere.
     */
    private void addAddress(String add) {
        address.add(add);
    }

    /**
     * Genera ed invia gli eventi. A seconda del comando passato come parametro
     * sa se deve creare le coppie tra il comando e ogni indirizzo salvato nella
     * lista, oppure se deve generare un evento solo con il comando ricevuto.
     * @param cmd comando ricevuto e con il quale inviare l'evento.
     */
    private void sendEvents(String cmd) {
        if (thisCommandNeedsAddres(cmd)) {
            for (int i = 0; i < address.size(); i++) {
                notifyEvent(new SwitchPressed(this, "X10", address.get(i), cmd));
                System.out.println(address.get(i) + " - " + cmd);
            }
        } else {
            notifyEvent(new SwitchPressed(this, "X10", "", cmd));
            System.out.println(cmd);
        }
    }

    /**
     * Restituisce true se la stringa passata corrisponde ad un indirizzo formato
     * da una lettera compresa tra A e P e due cifre comprese tra 01 e 16.
     * @param str stringa da controllare se contiene un indirizzo.
     * @return true se la stringa è un indirizzo X10 valido.
     */
    public static boolean isAddress(String str) {
        str = str.toUpperCase();

        // Se sono più o meno di 3 caratteri
        if (str.length() != 3) {
            return false;
        }

        // Il primo carattere non è tra A e P
        char lettera = str.charAt(0);
        if (lettera < 'A' || lettera > 'P') {
            return false;
        }

        // Il 2° e 3° carattere non compongono un numero compreso tra 01 e 16
        try {
            int num = Integer.parseInt(str.substring(1, 3));
            if (num < 1 || num > 16) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        return true;

    }

    /**
     * Restituisce true se la stringa passata corrisponde ad un comando formato
     * da una lettera compresa tra A e P e il codice comando.
     * @param str stringa da controllare se contiene un comando.
     * @return true se la stringa è un comando X10 valido.
     */
    public static boolean isCommand(String str) {
        str = str.toUpperCase();

        // Se sono più o meno di 3 o 4 caratteri
        if (str.length() < 3 || str.length() > 4) {
            return false;
        }

        // Il primo carattere non è tra A e P
        char lettera = str.charAt(0);
        if (lettera < 'A' || lettera > 'P') {
            return false;
        }

        // Il resto è un comando X10 valido
        str = str.substring(1);
        if (str.compareTo("ON") == 0) {
            return true;
        }
        if (str.compareTo("OFF") == 0) {
            return true;
        }
        if (str.compareTo("DIM") == 0) {
            return true;
        }
        if (str.compareTo("BGT") == 0) {
            return true;
        }
        if (str.compareTo("AUF") == 0) {
            return true;
        }
        if (str.compareTo("ALN") == 0) {
            return true;
        }
        if (str.compareTo("ALN") == 0) {
            return true;
        }
        if (str.compareTo("HRQ") == 0) {
            return true;
        }
        if (str.compareTo("HAK") == 0) {
            return true;
        }
        if (str.compareTo("PRG") == 0) {
            return true;
        }
        if (str.compareTo("SON") == 0) {
            return true;
        }
        if (str.compareTo("SOF") == 0) {
            return true;
        }
        if (str.compareTo("SRQ") == 0) {
            return true;
        }

        return false;

    }

    /**
     * Controlla se la stringa passata è un comando X10 che necessita di essere
     * accoppiato ad un indirizzo.
     * @param cmd stringa da controlalre.
     * @return true se la stringa è un comando che necessita un indirizzo.
     */
    public static boolean thisCommandNeedsAddres(String cmd) {
        if (!isCommand(cmd)) {
            return false;
        }
        cmd = cmd.substring(1);
        if (cmd.compareTo("ON") == 0) {
            return true;
        }
        if (cmd.compareTo("OFF") == 0) {
            return true;
        }
        if (cmd.compareTo("DIM") == 0) {
            return true;
        }
        if (cmd.compareTo("BGT") == 0) {
            return true;
        }
        if (cmd.compareTo("AUF") == 0) {
            return false;
        }
        if (cmd.compareTo("ALN") == 0) {
            return false;
        }
        if (cmd.compareTo("ALN") == 0) {
            return false;
        }
        if (cmd.compareTo("HRQ") == 0) {
            return true;
        }
        if (cmd.compareTo("HAK") == 0) {
            return false;
        }
        if (cmd.compareTo("PRG") == 0) {
            return true;
        }
        if (cmd.compareTo("SON") == 0) {
            return true;
        }
        if (cmd.compareTo("SOF") == 0) {
            return true;
        }
        if (cmd.compareTo("SRQ") == 0) {
            return true;
        }
        return false;
    }

    //TODO: move in PMix15Gatevay.java and create a Gateway Interface to add new gateway like CM11
    public static String parseReaded(String readed) {


        // Se ha letto messaggio vuoto ($<900029#)
        if (readed.compareToIgnoreCase(PMIX_NULL) == 0) {
            return "";
        }

        // Se ha letto un messaggio ack (è successo qualcosa che non va!!!)
//        if ((readed.compareToIgnoreCase(PMIX_ACK) == 0) || (readed.compareToIgnoreCase(PMIX_NACK) == 0)) {
//            System.err.println("Errore molto GRAVE, non dovrebbe mai essere letto un ack in questo momento, controllare che i metodi send e recive siano sincronizzati.");
//            return "";
//        }

        // Se non ho letto niente c'è un errore (probabilmente si è sollegato il gateway)
        if (readed.isEmpty()) {
            System.err.println("Errore molto GRAVE, nessun dato letto, probabilmente il PMix35 no è più online.");
            return "";
        }

        // Estraggo i dati dalla struttura del pacchetto
        // rimuovo la prima parte ($<9000) e l'ultima (CS#)
        readed = readed.substring(6, readed.length() - 3);

        // Cerco e rimuovo ND e NI

        if (readed.startsWith("ND")) {
            //boolean noiseDetected = (new Integer(readed.substring(2,4))==1);          // Noise detection
            //System.out.println("\t\t\t\t\t Noise detected   : " + noiseDetected);
            readed = readed.substring(4);

        } else if (readed.startsWith("NI")) {
            //int impedance = new Integer(readed.substring(2,6));                       // Network inmpedance
            //System.out.println("\t\t\t\t\t Network impedance: " + impedance);
            readed = readed.substring(6);

        }

        // Ho già analizzato tutto?
        if (readed.isEmpty()) {
            return "";
        }

        // Se LE rimuovo
        if (readed.startsWith("LE")) {
            return "";                                         // Line echo
        }        //if (readed.startsWith("LE")) readed = readed + "\techo";

        //int voltage = new Integer(readed.substring(3,6));                             // Voltage
        //System.out.println("\t\t\t\t\t Voltage: " + voltage);
        //int noise = new Integer(readed.substring(6,10));                              // Noise
        //System.out.println("\t\t\t\t\t Noise  : " + noise);
        readed = readed.substring(10).trim();
        return readed;
    }

    @Override
    protected void onInformationRequest() throws IOException, UnableToExecuteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void onRun() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
