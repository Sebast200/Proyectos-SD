package nivel1;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

public class Surtidor {
    private String id;
    private boolean estado; //Para comprobar si se esta utilizando el surtidor y asi no permitir actualizar el precio
    private Map<String, Combustible> combustibles;
    private Estanque estanque;

    public Surtidor(String _id, Estanque _estanque){
        this.id = _id;
        this.estado = false;
        this.combustibles = new HashMap<>();
        this.estanque = _estanque;
    }

    //SETTER Y GETTERS

    public void setEstado(boolean _estado){
        this.estado = _estado;
    }

    public void inicializarCombustible() {
        combustibles.put("93", new Combustible("93", 0, 0, 100.0, 0.0));
        combustibles.put("95", new Combustible("95", 0, 0, 100.0, 0.0));
        combustibles.put("97", new Combustible("97", 0, 0, 100.0, 0.0));
        combustibles.put("Diesel", new Combustible("Diesel", 0, 0, 100.0, 0.0));
        combustibles.put("Kerosene", new Combustible("Kerosene", 0, 0, 100.0, 0.0));
    }

    public synchronized boolean registrarCarga(String tipo, double litros) {
        if (estado && combustibles.containsKey(tipo) && estanque.extraer(tipo, litros)) {
            System.out.println("Registrando carga de combustible | "+ "Tipo: " +tipo+"Cantidad: "+litros+" Litros");
            combustibles.get(tipo).registrarCarga(litros);
            return true;
        }
        return false;
    }

    public synchronized boolean actualizarPrecio(String tipo, double nuevoPrecio) {
        if (estado && combustibles.containsKey(tipo)) {
            combustibles.get(tipo).actualizarPrecio(nuevoPrecio);
            return true;
        }
        return false;
    }

    public void guardarEstado(String rutaArchivo) throws IOException {
        List<String> nuevasLineas = new ArrayList<>();
        File archivo = new File(rutaArchivo);
        if (archivo.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    if (!linea.startsWith(id + ",")) {
                        nuevasLineas.add(linea);
                    }
                }
            }
        }

        for (Combustible c : combustibles.values()) {
            nuevasLineas.add(id + "," + c.getTipo() + "," + c.getLitrosConsumidos() + "," + c.getPrecioActual());

        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(rutaArchivo))) {
            for (String linea : nuevasLineas) {
                writer.write(linea);
                writer.newLine();
            }
        }
    }

    public void cargarEstado(String rutaArchivo) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(",");
                String surtidorId = partes[0];
                if (!surtidorId.equals(this.id)) continue;

                String tipo = partes[1];
                double cantidad = Double.parseDouble(partes[2]);
                double precio = Double.parseDouble(partes[3]);
                combustibles.put(tipo, new Combustible(tipo, 0, 0, precio, cantidad));
            }
        }
    }

    public synchronized boolean reponerCombustible(String tipo, double litros) {
        if (combustibles.containsKey(tipo)) {
            combustibles.get(tipo).reponer(litros);
            return true;
        }
        return false;
    }

    public void mostrarEstado() {
        System.out.println("Estado del surtidor " + id + ":");
        for (Combustible c : combustibles.values()) {
            System.out.println(c.getTipo() + " - Cargas: " + c.getCargasRealizadas() +", Litros entregados: " + 
            c.getLitrosConsumidos() + ", Precio: $" + c.getPrecioActual() + ", Disponible en estanque: " + 
            estanque.getNivel(c.getTipo()) + " litros");
        }
    }

}

class Combustible {
    private String tipo;
    private double ltConsumidos;
    private int cargasRealizadas;
    private double precioActual;
    private double cantidadDisponible;

    public Combustible(String _tipo, double _ltConsumidos, int _cargasRealizadas, double _precioActual, 
    double _cantidadDisponible) {
        this.tipo = _tipo;
        this.ltConsumidos = _ltConsumidos;
        this.cargasRealizadas = _cargasRealizadas;
        this.precioActual = _precioActual;
        this.cantidadDisponible = _cantidadDisponible;
    }

    //SETTERS Y GETTERS

    public String getTipo() {
        return this.tipo;
    }

    public double getCantidadDisponible() {
        return this.cantidadDisponible;
    }

    public double getPrecioActual() {
        return this.precioActual;
    }

    public int getCargasRealizadas() {
        return this.cargasRealizadas;
    }

    public double getLitrosConsumidos() {
        return this.ltConsumidos;
    }

    public synchronized void registrarCarga(double litros) {
        this.ltConsumidos += litros;
        this.cargasRealizadas++;
    }

    public synchronized void actualizarPrecio(double nuevoPrecio) {
        this.precioActual = nuevoPrecio;
    }

    public synchronized void reponer(double litros) {
        this.cantidadDisponible += litros;
    }

}

class Estanque{
    private Map<String, Double> niveles;

    public Estanque() {
        this.niveles = new HashMap<>();
        niveles.put("93", 1000.0);
        niveles.put("95", 1000.0);
        niveles.put("97", 1000.0);
        niveles.put("Diesel", 1000.0);
        niveles.put("Kerosene", 1000.0);
    }     

    public synchronized boolean extraer(String tipo, double litros) {
        if (niveles.containsKey(tipo) && niveles.get(tipo) >= litros) {
            niveles.put(tipo, niveles.get(tipo) - litros);
            return true;
        }
        return false;
    }

    public void guardarEstado(String rutaArchivo) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(rutaArchivo))) {
            for (Map.Entry<String, Double> entry : niveles.entrySet()) {
                writer.write(entry.getKey() + "," + entry.getValue());
                writer.newLine();
            }
        }
    }

    public void cargarEstado(String rutaArchivo) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(",");
                String tipo = partes[0];
                double cantidad = Double.parseDouble(partes[1]);
                niveles.put(tipo, cantidad);
            }
        }
    }

    public void mostrarEstado() {
        System.out.println("Estado del estanque:");
        for (Map.Entry<String, Double> entry : niveles.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue() + " litros");
        }
    }

    public synchronized double getNivel(String tipo) {
        return niveles.getOrDefault(tipo, 0.0);
    }

}