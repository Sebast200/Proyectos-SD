import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Surtidor {
    private String id;
    private boolean estado;
    private Map<String, Combustible> combustibles;
    private Map<String, Double> preciosPendientes;
    private PrintWriter salidaDistribuidor;

    public Surtidor(String _id){
        this.id = _id;
        this.estado = false;
        this.combustibles = new HashMap<>();
        this.preciosPendientes = new ConcurrentHashMap<>();
        this.salidaDistribuidor = null;
    }
    
    public void setSalidaDistribuidor(PrintWriter salida) {
        this.salidaDistribuidor = salida;
    }

    //SETTER Y GETTERS

    public void setEstado(boolean _estado){
        this.estado = _estado;
    }
    
    public boolean getEstado(){
        return this.estado;
    }

    public void inicializarCombustible() {
        combustibles.put("93", new Combustible("93", 0, 0, 100.0, 0.0));
        combustibles.put("95", new Combustible("95", 0, 0, 100.0, 0.0));
        combustibles.put("97", new Combustible("97", 0, 0, 100.0, 0.0));
        combustibles.put("Diesel", new Combustible("Diesel", 0, 0, 100.0, 0.0));
        combustibles.put("Kerosene", new Combustible("Kerosene", 0, 0, 100.0, 0.0));
    }

    public synchronized boolean registrarCarga(String tipo, double litros, String archivoTransacciones) {
        if (combustibles.containsKey(tipo)) {
            System.out.println("Registrando carga de combustible | "+ "Tipo: " +tipo+" Cantidad: "+litros+" Litros");
            combustibles.get(tipo).registrarCarga(litros);
            
            if (salidaDistribuidor != null) {
                salidaDistribuidor.println("TRANSACCION " + id + " " + tipo + " " + litros);
                System.out.println("[SYNC] Transacción enviada al distribuidor");
            } else {
                guardarTransaccionPendiente(tipo, litros, archivoTransacciones);
                System.out.println("[OFFLINE] Transacción guardada localmente (sin conexión al distribuidor)");
            }
            
            return true;
        }
        return false;
    }
    
    private synchronized void guardarTransaccionPendiente(String tipo, double litros, String archivoTransacciones) {
        try {
            File file = new File(archivoTransacciones);
            file.getParentFile().mkdirs();
            
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String linea = id + "|" + tipo + "|" + litros + "|" + timestamp;
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                writer.write(linea);
                writer.newLine();
            }
            
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo guardar transacción pendiente: " + e.getMessage());
        }
    }
    
    private synchronized void enviarTransaccionesPendientes(String archivoTransacciones) {
        File file = new File(archivoTransacciones);
        if (!file.exists()) {
            return;
        }
        
        int contador = 0;
        int descartadas = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String linea;
            System.out.println("\n[SYNC] Sincronizando transacciones pendientes...");
            
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split("\\|");
                if (partes.length >= 4) {
                    String surtidorId = partes[0];
                    String tipo = partes[1];
                    String litros = partes[2];
                    String timestamp = partes[3];
                    
                    if (surtidorId.equals(this.id)) {
                        if (salidaDistribuidor != null) {
                            salidaDistribuidor.println("TRANSACCION " + surtidorId + " " + tipo + " " + litros);
                            System.out.println("[SYNC] ✓ Transacción enviada: " + tipo + " " + litros + "L (" + timestamp + ")");
                            contador++;
                        }
                    } else {
                        descartadas++;
                    }
                }
            }
            
            if (descartadas > 0) {
                System.out.println("[SYNC] " + descartadas + " transacción(es) descartada(s) (ID diferente)");
            }
            System.out.println("[SYNC] " + contador + " transacción(es) sincronizada(s)\n");
            
        } catch (IOException e) {
            System.err.println("[ERROR] Error al leer transacciones pendientes: " + e.getMessage());
            return;
        }
        
        if (contador > 0 || descartadas > 0) {
            try {
                new FileWriter(file, false).close();
                System.out.println("[PERSISTENCIA] ✓ Archivo de pendientes limpiado");
            } catch (IOException e) {
                System.err.println("[ERROR] No se pudo limpiar archivo de pendientes: " + e.getMessage());
            }
        }
    }

    public synchronized boolean actualizarPrecio(String tipo, double nuevoPrecio) {
        if (combustibles.containsKey(tipo)) {
            if (estado) {
                preciosPendientes.put(tipo, nuevoPrecio);
                System.out.println("[PRECIO] Surtidor ocupado vendiendo. Precio de " + tipo + " pendiente: $" + nuevoPrecio);
                return false;
            } else {
                combustibles.get(tipo).actualizarPrecio(nuevoPrecio);
                System.out.println("[PRECIO] ✓ Precio de " + tipo + " actualizado a $" + nuevoPrecio);
                return true;
            }
        }
        return false;
    }
    
    private synchronized void aplicarPreciosPendientes() {
        if (!preciosPendientes.isEmpty()) {
            System.out.println("\n[PRECIO] Aplicando " + preciosPendientes.size() + " precio(s) pendiente(s)...");
            for (Map.Entry<String, Double> entry : preciosPendientes.entrySet()) {
                String tipo = entry.getKey();
                double precio = entry.getValue();
                if (combustibles.containsKey(tipo)) {
                    combustibles.get(tipo).actualizarPrecio(precio);
                    System.out.println("[PRECIO] ✓ " + tipo + " actualizado a $" + precio);
                }
            }
            preciosPendientes.clear();
            System.out.println("[PRECIO] Todos los precios pendientes aplicados\n");
        }
    }

    public void guardarEstado(String rutaArchivo) throws IOException {
        List<String> nuevasLineas = new ArrayList<>();
        File archivo = new File(rutaArchivo);
        if (archivo.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    String lineaId = linea.split(",")[0];
                    if (!lineaId.equals(id)) {
                        try {
                            int idNum = Integer.parseInt(lineaId);
                            if (idNum >= 1 && idNum <= 4) {
                                nuevasLineas.add(linea);
                            }
                        } catch (NumberFormatException e) {
                        }
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
        inicializarCombustible();
        
        File archivo = new File(rutaArchivo);
        if (!archivo.exists()) {
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            boolean encontrado = false;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(",");
                if (partes.length < 4) continue;
                
                String surtidorId = partes[0];
                if (!surtidorId.equals(this.id)) continue;

                String tipo = partes[1];
                double litrosConsumidos = Double.parseDouble(partes[2]);
                double precio = Double.parseDouble(partes[3]);

                if (combustibles.containsKey(tipo)) {
                    int cargas = (int)(litrosConsumidos / 10);
                    combustibles.put(tipo, new Combustible(tipo, litrosConsumidos, cargas, precio, 0.0));
                    encontrado = true;
                }
            }
            
            if (!encontrado) {
                System.out.println("[INFO] No se encontraron datos previos para surtidor " + this.id);
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
            c.getLitrosConsumidos() + ", Precio: $" + c.getPrecioActual());
        }
    }
    
    public String getId() {
        return this.id;
    }
    
    public void conectarADistribuidor(String host, int puerto, String archivoEstado, String archivoTransacciones) {
        new Thread(() -> {
            while (true) {
                try (
                    Socket socket = new Socket(host, puerto);
                    BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter salida = new PrintWriter(socket.getOutputStream(), true)
                ) {
                    System.out.println("[DISTRIBUIDOR] ✓ Conectado al distribuidor en " + host + ":" + puerto);
                    this.setSalidaDistribuidor(salida);
                    String mensaje = entrada.readLine();
                    if (mensaje != null && mensaje.equals("IDENTIFICAR")) {
                        salida.println("ID:" + this.id);
                    }

                    String confirmacion = entrada.readLine();
                    if (confirmacion != null) {
                        System.out.println("[DISTRIBUIDOR] " + confirmacion);
                    }
        
                    enviarTransaccionesPendientes(archivoTransacciones);
                    
                    while ((mensaje = entrada.readLine()) != null) {
                        if (mensaje.startsWith("OK:") || mensaje.startsWith("ERROR:") || mensaje.equals("ACK")) {
                            if (!mensaje.contains("Transacción registrada")) {
                                System.out.println("[DISTRIBUIDOR] " + mensaje);
                            }
                            continue;
                        }
                        
                        String[] partes = mensaje.trim().split("\\s+");
                        
                        if (partes.length == 0) continue;
                        String comando = partes[0].toUpperCase();
                        
                        switch (comando) {
                            case "PRECIO":
                                if (partes.length == 3) {
                                    String tipo = partes[1];
                                    try {
                                        double precio = Double.parseDouble(partes[2]);
                                        boolean actualizado = this.actualizarPrecio(tipo, precio);
                                        if (actualizado) {
                                            salida.println("OK: Precio de " + tipo + " actualizado a $" + precio);
                                            this.guardarEstado(archivoEstado);
                                            System.out.println("[DISTRIBUIDOR] ✓ Precio actualizado: " + tipo + " = $" + precio);
                                        } else {
                                            salida.println("PENDIENTE: Precio de " + tipo + " se aplicará al finalizar venta");
                                        }
                                    } catch (NumberFormatException e) {
                                        salida.println("ERROR: Precio inválido");
                                    } catch (IOException e) {
                                        salida.println("ERROR: No se pudo guardar el estado");
                                    }
                                }
                                break;
                                
                            case "ESTADO_SURTIDOR":
                                StringBuilder estado = new StringBuilder();
                                estado.append("ESTADO:" + this.id + "|");
                                for (Combustible c : combustibles.values()) {
                                    estado.append(c.getTipo()).append(":")
                                          .append(c.getPrecioActual()).append(":")
                                          .append(c.getLitrosConsumidos()).append(":")
                                          .append(c.getCargasRealizadas()).append(";");
                                }
                                salida.println(estado.toString());
                                break;
                                
                            default:
                        }
                    }
                    
                    System.out.println("[DISTRIBUIDOR] Desconectado");
                    this.setSalidaDistribuidor(null);
                    
                } catch (IOException e) {
                    this.setSalidaDistribuidor(null);
                    System.out.println("[DISTRIBUIDOR] Sin conexión con el distribuidor");
                    System.out.println("[DISTRIBUIDOR] Reintentando en 1 minuto...");
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private static String asignarIdAutomatico(String rutaArchivo, String archivoTransacciones) {
        boolean[] idsUsados = new boolean[5];
        String idTransacciones = null;
        
        File archivoTrans = new File(archivoTransacciones);
        if (archivoTrans.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(archivoTransacciones))) {
                String linea = reader.readLine();
                if (linea != null) {
                    String[] partes = linea.split("\\|");
                    if (partes.length > 0) {
                        idTransacciones = partes[0].trim();
                        System.out.println("[INFO] ID encontrado en transacciones pendientes: " + idTransacciones);
                        return idTransacciones;
                    }
                }
            } catch (IOException e) {
                System.err.println("[WARN] No se pudo leer archivo de transacciones: " + e.getMessage());
            }
        }
        
        File archivo = new File(rutaArchivo);
        if (archivo.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    String[] partes = linea.split(",");
                    if (partes.length > 0) {
                        String id = partes[0].trim();
                        try {
                            int idNum = Integer.parseInt(id);
                            if (idNum >= 1 && idNum <= 4) {
                                idsUsados[idNum] = true;
                            }
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("[WARN] No se pudo leer archivo de estado: " + e.getMessage());
            }
        }
        
        for (int i = 1; i <= 4; i++) {
            if (!idsUsados[i]) {
                return String.valueOf(i);
            }
        }
        
        return String.valueOf((int)(Math.random() * 4) + 1);
    }

    public static void main(String[] args) {
        String servidorIP = args.length > 0 ? args[0] : "servidor";
        int puerto = 5000;
        String archivoEstado = "/app/data/estado_surtidor.txt";
        String archivoTransacciones = "/app/data/transacciones_pendientes.txt";
        
        String surtidorId = asignarIdAutomatico(archivoEstado, archivoTransacciones);
        System.out.println("[INFO] ID asignado automáticamente: " + surtidorId);
        
        Surtidor surtidor = new Surtidor(surtidorId);
        
        try {
            surtidor.cargarEstado(archivoEstado);
            System.out.println("[INFO] Surtidor " + surtidorId + " listo");
        } catch (IOException e) {
            System.err.println("[ERROR] Error al cargar estado: " + e.getMessage());
            surtidor.inicializarCombustible();
        }
        
        File archivo = new File(archivoEstado);
        if (!archivo.exists()) {
            try {
                new File("/app/data").mkdirs();
                surtidor.guardarEstado(archivoEstado);
                System.out.println("[INFO] Estado inicial guardado");
            } catch (IOException e) {
                System.err.println("[ADVERTENCIA] No se pudo crear archivo de estado");
            }
        }
        
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║  TERMINAL SURTIDOR " + surtidorId + " - Sistema de Gestión ║");
        System.out.println("╚══════════════════════════════════════════╝");
        
        surtidor.mostrarEstado();
        
        String distribuidorHost = System.getenv().getOrDefault("DISTRIBUIDOR_HOST", "distribuidor");
        int distribuidorPuerto = Integer.parseInt(System.getenv().getOrDefault("DISTRIBUIDOR_PORT", "6000"));
        System.out.println("\n[DISTRIBUIDOR] Conectando a " + distribuidorHost + ":" + distribuidorPuerto + "...");
        surtidor.conectarADistribuidor(distribuidorHost, distribuidorPuerto, archivoEstado, archivoTransacciones);

        try (
            Socket socket = new Socket(servidorIP, puerto);
            BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("\nConectado al Estanque: " + servidorIP + ":" + puerto + "\n");

            String bienvenida;
            while ((bienvenida = entrada.readLine()) != null) {
                if (bienvenida.contains("Tipos:") || bienvenida.contains("Kerosene")) break;
            }

            System.out.println(">>> Comandos del Surtidor:");
            System.out.println("  EXTRAER <tipo> <litros>   - Extraer combustible del estanque");
            System.out.println("  CONSULTAR <tipo>          - Ver nivel disponible en estanque");
            System.out.println("  ESTADO                    - Ver estado del estanque");
            System.out.println("  CARGAR <tipo> <litros>    - Registrar venta de combustible");
            System.out.println("  PRECIO <tipo> <precio>    - Actualizar precio de combustible");
            System.out.println("  MISURTIDOR                - Ver estado de este surtidor");
            System.out.println("  SALIR                     - Desconectar\n");

            String mensaje;
            while (true) {
                System.out.print(surtidorId + "> ");
                mensaje = teclado.readLine();
                if (mensaje == null || mensaje.equalsIgnoreCase("salir")) {
                    salida.println("SALIR");
                    try {
                        surtidor.guardarEstado(archivoEstado);
                        System.out.println("[INFO] Estado guardado correctamente");
                    } catch (IOException e) {
                        System.err.println("[ERROR] No se pudo guardar el estado: " + e.getMessage());
                    }
                    break;
                }
                
                String[] partes = mensaje.trim().split("\\s+");
                if (partes.length == 0) continue;
                
                String comando = partes[0].toUpperCase();
                
                if (comando.equals("CARGAR")) {
                    if (partes.length != 3) {
                        System.out.println("ERROR: Formato incorrecto. Usa: CARGAR <tipo> <litros>");
                        continue;
                    }
                    String tipo = partes[1];
                    try {
                        double litros = Double.parseDouble(partes[2]);
                        if (surtidor.registrarCarga(tipo, litros, archivoTransacciones)) {
                            System.out.println("OK: Registrada venta de " + litros + " L de " + tipo);
                            surtidor.guardarEstado(archivoEstado);
                        } else {
                            System.out.println("ERROR: No se pudo registrar la carga");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("ERROR: Los litros deben ser un número");
                    } catch (IOException e) {
                        System.err.println("ERROR: No se pudo guardar el estado");
                    }
                    continue;
                }
                
                if (comando.equals("PRECIO")) {
                    if (partes.length != 3) {
                        System.out.println("ERROR: Formato incorrecto. Usa: PRECIO <tipo> <precio>");
                        continue;
                    }
                    String tipo = partes[1];
                    try {
                        double precio = Double.parseDouble(partes[2]);
                        if (surtidor.actualizarPrecio(tipo, precio)) {
                            System.out.println("OK: Precio de " + tipo + " actualizado a $" + precio);
                            surtidor.guardarEstado(archivoEstado);
                        } else {
                            System.out.println("PENDIENTE: Precio se aplicará al finalizar la venta en curso");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("ERROR: El precio debe ser un número");
                    } catch (IOException e) {
                        System.err.println("ERROR: No se pudo guardar el estado");
                    }
                    continue;
                }
                
                if (comando.equals("MISURTIDOR")) {
                    System.out.println("\n=== ESTADO DEL SURTIDOR " + surtidorId + " ===");
                    surtidor.mostrarEstado();
                    System.out.println("===============================\n");
                    continue;
                }
                
                if (comando.equals("EXTRAER")) {
                    if (surtidor.getEstado()) {
                        System.out.println("ERROR: Surtidor ocupado. Hay una venta en proceso.");
                        System.out.println("Por favor espere a que finalice la transacción actual.");
                        continue;
                    }
                }
                
                salida.println(mensaje);

                String respuesta = entrada.readLine();
                if (respuesta != null) {
                    System.out.println(respuesta);

                    if (comando.equals("EXTRAER") && respuesta.startsWith("OK:")) {
                        try {
                            String tipo = partes[1];
                            double litros = Double.parseDouble(partes[2]);

                            surtidor.setEstado(true);
                            System.out.println("\n╔═══════════════════════════════════════════╗");
                            System.out.println("║  🔄 VENTA EN PROCESO - Surtidor " + surtidorId + "        ║");
                            System.out.println("║  Combustible: " + tipo + " | " + litros + " L              ║");
                            System.out.println("║  Tiempo estimado: 20 segundos          ║");
                            System.out.println("╚═══════════════════════════════════════════╝\n");

                            if (surtidor.registrarCarga(tipo, litros, archivoTransacciones)) {
                                System.out.println("[SURTIDOR] Registrada venta de " + litros + " L de " + tipo);
                                surtidor.guardarEstado(archivoEstado);
                            }

                            System.out.println("[VENTA] Procesando transacción");
                            for (int i = 1; i <= 20; i++) {
                                Thread.sleep(1000);
                                if (i % 5 == 0) {
                                    System.out.println("[VENTA] Progreso: " + i + "/20 segundos...");
                                }
                            }

                            surtidor.setEstado(false);
                            System.out.println("\n╔═══════════════════════════════════════════╗");
                            System.out.println("║  ✓ VENTA COMPLETADA - Surtidor " + surtidorId + "        ║");
                            System.out.println("╚═══════════════════════════════════════════╝\n");
                            
                            surtidor.aplicarPreciosPendientes();
                            
                        } catch (InterruptedException ie) {
                            System.err.println("[ERROR] Venta interrumpida");
                            surtidor.setEstado(false);
                        } catch (Exception e) {
                            System.err.println("[ERROR] No se pudo registrar la carga en el surtidor");
                            surtidor.setEstado(false);
                        }
                    }
                    
                    if (mensaje.trim().toUpperCase().equals("ESTADO")) {
                        String linea;
                        while ((linea = entrada.readLine()) != null) {
                            System.out.println(linea);
                            if (linea.contains("====")) break;
                        }
                    }
                }
            }
            
            System.out.println("\n[INFO] Desconectado del estanque.");
            
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo conectar al estanque: " + e.getMessage());
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
