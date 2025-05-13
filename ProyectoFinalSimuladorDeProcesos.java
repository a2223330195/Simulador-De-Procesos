import java.util.*;
import java.util.concurrent.*;

class PCB {
    static int counter = 0;
    int pid;
    String estado;
    int prioridad;
    int tiempoEjecucion;
    int tiempoOriginal;
    int tiempoLlegada;
    int tiempoFinalizacion;
    int tiempoEspera;
    int tiempoRetorno;
    List<String> recursosAsignados = new ArrayList<>();
    List<String> recursosEsperados = new ArrayList<>();
    CausaTerminacion causaTerminacion;
    Map<Integer, List<String>> mensajes = new HashMap<>();

    // Constructor para inicializar un proceso con prioridad y tiempo de ejecución
    public PCB(int prioridad, int tiempoEjecucion) {
        this.pid = ++counter;
        this.estado = "Listo";
        this.prioridad = prioridad;
        this.tiempoEjecucion = tiempoEjecucion;
        this.tiempoOriginal = tiempoEjecucion;
        this.tiempoLlegada = ProyectoFinalSimuladorDeProcesos.tiempoGlobal++;
    }

    // Método para enviar un mensaje a otro proceso
    public void enviarMensaje(int pidDestino, String contenido) {
        for (PCB p : ProyectoFinalSimuladorDeProcesos.planificador.listaProcesos) {
            if (p.pid == pidDestino) {
                if (!p.mensajes.containsKey(this.pid)) {
                    p.mensajes.put(this.pid, new ArrayList<>());
                }
                p.mensajes.get(this.pid).add(contenido);
                Log.registrar("COMUNICACIÓN", "PID " + this.pid + " → PID " + pidDestino + ": mensaje enviado");
                return;
            }
        }
        Log.registrar("ERROR", "No se envió mensaje: PID " + pidDestino + " no encontrado");
    }

    // Método para leer los mensajes recibidos por el proceso
    public void leerMensajes() {
        if (mensajes.isEmpty()) {
            System.out.println("No hay mensajes para este proceso");
            return;
        }
        
        System.out.println("\n=== MENSAJES PARA PID " + pid + " ===");
        for (Map.Entry<Integer, List<String>> entry : mensajes.entrySet()) {
            System.out.println("De PID " + entry.getKey() + ":");
            for (String msg : entry.getValue()) {
                System.out.println("- " + msg);
            }
        }
        mensajes.clear();
    }
}

enum CausaTerminacion {
    NORMAL("Ejecución completada"),
    ERROR("Error durante ejecución"),
    INTERBLOQUEO("Interbloqueo detectado"),
    USUARIO("Terminado por usuario");
    
    private String descripcion;
    
    CausaTerminacion(String descripcion) {
        this.descripcion = descripcion;
    }
    
    @Override
    public String toString() {
        return descripcion;
    }
}

class Log {
    // Método para registrar eventos en el sistema
    public static void registrar(String tipo, String mensaje) {
        System.out.println("[" + tipo + "] " + mensaje);
    }
}

class Recurso {
    int memoriaDisponible = 4096;    
    boolean cpuDisponible = true;
    Map<Integer, List<String>> recursosEsperados = new HashMap<>();
    Map<Integer, Integer> memoriaAsignadaPorProceso = new HashMap<>();

    // Método para solicitar recursos para un proceso
    public synchronized boolean solicitar(PCB p, int memoria) {
        if (memoria <= memoriaDisponible && cpuDisponible) {
            memoriaDisponible -= memoria;
            cpuDisponible = false;
            p.recursosAsignados.add("CPU");
            p.recursosAsignados.add(memoria + "MB RAM");
            memoriaAsignadaPorProceso.put(p.pid, memoria);
            Log.registrar("RECURSO", "PID " + p.pid + " obtuvo CPU y " + memoria + "MB de RAM");
            recursosEsperados.remove(p.pid);
            p.recursosEsperados.clear();
            mostrarCambioRecursos("Asignados a PID " + p.pid);
            return true;
        } else {
            p.estado = "Bloqueado";
            p.recursosEsperados.clear();
            
            List<String> recursos = new ArrayList<>();
            if (memoria > memoriaDisponible) {
                recursos.add(memoria + "MB RAM");
                p.recursosEsperados.add(memoria + "MB RAM");
            }
            if (!cpuDisponible) {
                recursos.add("CPU");
                p.recursosEsperados.add("CPU");
            }
            recursosEsperados.put(p.pid, recursos);
            
            if (detectarInterbloqueo()) {
                resolverInterbloqueo(p);
            }
            
            Log.registrar("RECURSO", "PID " + p.pid + " bloqueado esperando recursos: " + 
                     String.join(", ", recursos) + " (RAM disponible: " + 
                     memoriaDisponible + "MB, CPU: " + (cpuDisponible ? "disponible" : "no disponible") + ")");
            return false;
        }
    }

    // Método para detectar interbloqueos en el sistema
    private boolean detectarInterbloqueo() {
        if (recursosEsperados.size() < 2) return false;
        
        int procesosCpuEsperados = 0;
        for (Map.Entry<Integer, List<String>> entry : recursosEsperados.entrySet()) {
            if (entry.getValue().contains("CPU")) {
                procesosCpuEsperados++;
            }
        }
        
        if (procesosCpuEsperados >= 2 && !cpuDisponible) {
            Log.registrar("SISTEMA", "¡INTERBLOQUEO DETECTADO! Múltiples procesos esperando CPU");
            return true;
        }
        
        return false;
    }
    
    // Método para resolver interbloqueos terminando un proceso
    private void resolverInterbloqueo(PCB procesoActual) {
        procesoActual.estado = "Terminado";
        procesoActual.causaTerminacion = CausaTerminacion.INTERBLOQUEO;
        Log.registrar("INTERBLOQUEO", "PID " + procesoActual.pid + " terminado para resolver interbloqueo");
        recursosEsperados.remove(procesoActual.pid);
        procesoActual.recursosEsperados.clear();
    }

    // Método para liberar recursos asignados a un proceso
    public synchronized void liberar(PCB p) {
        boolean cpuLiberada = false;
        
        if (p.recursosAsignados.isEmpty()) {
            Log.registrar("RECURSO", "PID " + p.pid + " no tenía recursos asignados");
            return;
        }
        
        if (memoriaAsignadaPorProceso.containsKey(p.pid)) {
            int memoriaAsignada = memoriaAsignadaPorProceso.get(p.pid);
            memoriaDisponible += memoriaAsignada;
            Log.registrar("RECURSO", "PID " + p.pid + " liberó " + memoriaAsignada + "MB de RAM");
            memoriaAsignadaPorProceso.remove(p.pid);
        }
        
        for (String r : p.recursosAsignados) {
            if (r.equals("CPU")) {
                cpuDisponible = true;
                cpuLiberada = true;
                Log.registrar("RECURSO", "PID " + p.pid + " liberó CPU");
                break;
            }
        }
        
        if (!cpuLiberada) {
            Log.registrar("RECURSO", "PID " + p.pid + " no tenía la CPU asignada");
        }
        
        p.recursosAsignados.clear();
        mostrarCambioRecursos("Liberados por PID " + p.pid);
        
        desbloquearProcesos();
    }
    
    // Método para desbloquear procesos que estaban esperando recursos
    private void desbloquearProcesos() {
        List<Integer> procesosDesbloqueados = new ArrayList<>();
        
        for (Map.Entry<Integer, List<String>> entry : recursosEsperados.entrySet()) {
            int pid = entry.getKey();
            List<String> recursosNecesitados = entry.getValue();
            
            boolean puedeDesbloquear = true;
            int memoriaRequerida = 0;
            boolean requiereCPU = false;
            
            for (String recurso : recursosNecesitados) {
                if (recurso.contains("MB RAM")) {
                    memoriaRequerida = Integer.parseInt(recurso.split("MB")[0]);
                }
                if (recurso.equals("CPU")) {
                    requiereCPU = true;
                }
            }
            
            if (requiereCPU && !cpuDisponible) {
                puedeDesbloquear = false;
            }
            if (memoriaRequerida > memoriaDisponible) {
                puedeDesbloquear = false;
            }
            
            if (puedeDesbloquear) {
                procesosDesbloqueados.add(pid);
                for (PCB p : ProyectoFinalSimuladorDeProcesos.planificador.listaProcesos) {
                    if (p.pid == pid && p.estado.equals("Bloqueado")) {
                        p.estado = "Listo";
                        p.recursosEsperados.clear();
                        Log.registrar("RECURSO", "PID " + pid + " desbloqueado, recursos disponibles");
                        break;
                    }
                }
            }
        }
        
        for (Integer pid : procesosDesbloqueados) {
            recursosEsperados.remove(pid);
        }
    }
    
    // Método para mostrar el estado de los recursos
    private void mostrarCambioRecursos(String motivo) {
        System.out.println("\n--- ACTUALIZACIÓN DE RECURSOS (" + motivo + ") ---");
        System.out.println("Memoria disponible: " + memoriaDisponible + "MB");
        System.out.println("CPU disponible: " + (cpuDisponible ? "Sí" : "No"));
        System.out.println("---------------------------------------");
    }
    
    @Override
    public String toString() {
        return "Memoria disponible: " + memoriaDisponible + "MB, CPU disponible: " + cpuDisponible;
    }
}

class Planificador {
    Queue<PCB> colaFCFS = new LinkedList<>();
    PriorityQueue<PCB> colaPrioridad = new PriorityQueue<>(Comparator.comparingInt(p -> p.prioridad));
    PriorityQueue<PCB> colaSJF = new PriorityQueue<>(Comparator.comparingInt(p -> p.tiempoEjecucion));
    Queue<PCB> colaRR = new LinkedList<>();
    List<PCB> listaProcesos = new ArrayList<>();
    String algoritmo;
    int quantum;

    // Constructor para inicializar el planificador con un algoritmo y quantum
    public Planificador(String algoritmo, int quantum) {
        this.algoritmo = algoritmo;
        this.quantum = quantum;
        Log.registrar("PLANIFICADOR", "Inicializado con algoritmo: " + algoritmo + 
                     (algoritmo.equals("RoundRobin") ? " (quantum: " + quantum + " unidades)" : ""));
    }

    // Método para agregar un proceso a la cola correspondiente
    public void agregarProceso(PCB p) {
        listaProcesos.add(p);
        switch (algoritmo) {
            case "FCFS": colaFCFS.offer(p); break;
            case "Prioridad": colaPrioridad.offer(p); break;
            case "RoundRobin": colaRR.offer(p); break;
            case "SJF": colaSJF.offer(p); break;
        }
        Log.registrar("PLANIFICADOR", "Proceso " + p.pid + " agregado a cola de " + algoritmo);
    }

    // Método para obtener el siguiente proceso según el algoritmo
    public PCB obtenerSiguienteProceso() {
        PCB p = null;
        switch (algoritmo) {
            case "FCFS": p = colaFCFS.poll(); break;
            case "Prioridad": p = colaPrioridad.poll(); break;
            case "SJF": p = colaSJF.poll(); break;
            case "RoundRobin": 
                p = colaRR.poll(); 
                if (p != null && p.estado.equals("Listo")) {
                    colaRR.offer(p); 
                }
                break;
        }
        if (p != null) {
            Log.registrar("PLANIFICADOR", "Proceso " + p.pid + " seleccionado para ejecución");
        }
        return p;
    }

    // Método para mostrar la lista de procesos en el sistema
    public void mostrarProcesos() {
        System.out.println("\n┌" + "─".repeat(100) + "┐");
        System.out.println("│" + String.format("%-98s", " LISTA DE PROCESOS") + "  │");
        System.out.println("├" + "─".repeat(100) + "┤");
        
        System.out.println("│ " + String.format("%-98s", " Algoritmo de planificación: " + algoritmo + 
            (algoritmo.equals("RoundRobin") ? " (Quantum: " + quantum + " unidades)" : "")));
        
        switch (algoritmo) {
            case "FCFS":
                System.out.println("│ " + String.format("%-98s", " Descripción: First Come First Served - Procesos atendidos en orden de llegada"));
                break;
            case "SJF":
                System.out.println("│ " + String.format("%-98s", " Descripción: Shortest Job First - Prioriza procesos con menor tiempo de ejecución"));
                break;
            case "RoundRobin":
                System.out.println("│ " + String.format("%-98s", " Descripción: Round Robin - Asigna tiempo equitativo por turnos (quantum: " + quantum + ")"));
                break;
            case "Prioridad":
                System.out.println("│ " + String.format("%-98s", " Descripción: Prioridad - Procesos ordenados por valor de prioridad (menor número = mayor prioridad)"));
                break;
        }
        
        if (listaProcesos.isEmpty()) {
            System.out.println("│" + String.format("%-98s", " No hay procesos en el sistema"));
            System.out.println("└" + "─".repeat(100) + "┘");
            return;
        }
        
        System.out.println("├" + "─".repeat(100) + "┤");
        System.out.println("│ " + String.format("%-5s %-12s %-9s %-8s %-16s %-15s %-28s", 
                         "PID", "Estado", "Prioridad", "Tiempo", "Recursos", "Terminación", "Marcos de memoria"));
        System.out.println("├" + "─".repeat(100) + "┤");
        
        Map<Integer, List<Integer>> marcosPorProceso = new HashMap<>();
        int marcoDisponible = 0;
        
        for (PCB p : listaProcesos) {
            if (!p.estado.equals("Terminado") && !marcosPorProceso.containsKey(p.pid)) {
                int numMarcos = (p.estado.equals("Ejecutando")) ? 4 : 2;
                List<Integer> marcos = new ArrayList<>();
                for (int i = 0; i < numMarcos; i++) {
                    marcos.add(marcoDisponible++);
                }
                marcosPorProceso.put(p.pid, marcos);
            }
            
            String recursos = p.recursosAsignados.isEmpty() ? "Ninguno" : String.join(", ", p.recursosAsignados);
            if (recursos.length() > 15) recursos = recursos.substring(0, 12) + "...";
            
            if (p.estado.equals("Bloqueado") && !p.recursosEsperados.isEmpty()) {
                String esperando = String.join(", ", p.recursosEsperados);
                if (esperando.length() > 12) esperando = esperando.substring(0, 9) + "...";
                recursos = "Espera: " + esperando;
            }
            
            String marcosInfo = "N/A";
            if (marcosPorProceso.containsKey(p.pid)) {
                List<Integer> marcos = marcosPorProceso.get(p.pid);
                marcosInfo = "Marcos: " + marcos.toString().replace("[", "").replace("]", "");
                if (marcosInfo.length() > 27) marcosInfo = marcosInfo.substring(0, 24) + "...";
            }
            
            System.out.println("│ " + String.format("%-5d %-12s %-9d %-8d %-16s %-15s %-28s", 
                             p.pid, p.estado, p.prioridad, p.tiempoEjecucion, 
                             recursos,
                             p.causaTerminacion != null ? p.causaTerminacion : "",
                             marcosInfo));
            
            if (!p.mensajes.isEmpty()) {
                int totalMensajes = p.mensajes.values().stream().mapToInt(List::size).sum();
                System.out.println("│ " + String.format("%-98s", "   └─ Mensajes pendientes: " + totalMensajes));
            }
            
            if (p.estado.equals("Ejecutando")) {
                System.out.println("│ " + String.format("%-98s", "   └─ Detalles PCB: ID=" + p.pid + 
                    " Prioridad=" + p.prioridad + " EstadoActual=" + p.estado +
                    " TiempoRestante=" + p.tiempoEjecucion));
            }
        }
        
        System.out.println("├" + "─".repeat(100) + "┤");
        System.out.println("│ " + String.format("%-98s", " RESUMEN DE MEMORIA"));
        
        int procesosActivos = (int) listaProcesos.stream()
            .filter(p -> !p.estado.equals("Terminado"))
            .count();
            
        int totalMarcos = marcosPorProceso.values().stream()
            .mapToInt(List::size)
            .sum();
            
        System.out.println("│ " + String.format("%-98s", " Total marcos asignados: " + totalMarcos + 
            " | Procesos activos: " + procesosActivos));
        System.out.println("│ " + String.format("%-98s", " Memoria física utilizada: " + 
            (totalMarcos * 4) + "MB de " + ProyectoFinalSimuladorDeProcesos.recurso.memoriaDisponible + "MB"));
        
        System.out.println("└" + "─".repeat(100) + "┘");
    }

    // Método para mostrar el estado de las colas de planificación
    public void mostrarEstadoColas() {
        System.out.println("\n┌" + "─".repeat(60) + "┐");
        System.out.println("│" + String.format("%-58s", " ESTADO DE LAS COLAS") + "  │");
        System.out.println("├" + "─".repeat(60) + "┤");
        
        System.out.println("│ Algoritmo: " + String.format("%-46s", algoritmo + 
                          (algoritmo.equals("RoundRobin") ? " (quantum: " + quantum + " unidades)" : "")) + "  │");
        
        Queue<PCB> colaActiva = null;
        switch (algoritmo) {
            case "FCFS": colaActiva = colaFCFS; break;
            case "Prioridad": colaActiva = colaPrioridad; break;
            case "SJF": colaActiva = colaSJF; break;
            case "RoundRobin": colaActiva = colaRR; break;
        }
        
        System.out.println("│" + String.format("%-58s", " Procesos en cola: " + (colaActiva.isEmpty() ? "Vacía" : "")) + "  │");
        
        if (colaActiva.isEmpty()) {
            System.out.println("└" + "─".repeat(60) + "┘");
            return;
        }
        
        System.out.println("├" + "─".repeat(60) + "┤");
        System.out.println("│ " + String.format("%-3s %-6s %-11s %-10s %-22s", 
                         "Pos", "PID", "Prioridad", "Tiempo", "Estado") + "│");
        System.out.println("├" + "─".repeat(60) + "┤");
        
        int pos = 1;
        for (PCB p : colaActiva) {
            System.out.println("│ " + String.format("%-3d %-6d %-11d %-10d %-22s", 
                             pos++, p.pid, p.prioridad, p.tiempoEjecucion, p.estado) + "│");
        }
        System.out.println("└" + "─".repeat(60) + "┘");
    }
    
    // Método para actualizar las colas de planificación
    public void actualizarColas() {
        switch (algoritmo) {
            case "FCFS":
                colaFCFS.clear();
                for (PCB p : listaProcesos) {
                    if (p.estado.equals("Listo")) {
                        colaFCFS.offer(p);
                    }
                }
                break;
            case "Prioridad":
                colaPrioridad.clear();
                for (PCB p : listaProcesos) {
                    if (p.estado.equals("Listo")) {
                        colaPrioridad.offer(p);
                    }
                }
                break;
            case "SJF":
                colaSJF.clear();
                for (PCB p : listaProcesos) {
                    if (p.estado.equals("Listo")) {
                        colaSJF.offer(p);
                    }
                }
                break;
            case "RoundRobin":
                colaRR.clear();
                for (PCB p : listaProcesos) {
                    if (p.estado.equals("Listo")) {
                        colaRR.offer(p);
                    }
                }
                break;
        }
    }
}

class Buffer {
    Queue<Integer> buffer = new LinkedList<>();
    int capacidad = 5;
    Semaphore lleno = new Semaphore(0);
    Semaphore vacio = new Semaphore(5);
    Semaphore mutex = new Semaphore(1);

    // Método para producir un elemento en el buffer
    public void producir(int item) throws InterruptedException {
        vacio.acquire();
        mutex.acquire();
        buffer.offer(item);
        Log.registrar("PRODUCTOR", "Producido: " + item + " (buffer: " + buffer.size() + "/" + capacidad + ")");
        mutex.release();
        lleno.release();
    }

    // Método para consumir un elemento del buffer
    public void consumir() throws InterruptedException {
        lleno.acquire();
        mutex.acquire();
        int item = buffer.poll();
        Log.registrar("CONSUMIDOR", "Consumido: " + item + " (buffer: " + buffer.size() + "/" + capacidad + ")");
        mutex.release();
        vacio.release();
    }
}

public class ProyectoFinalSimuladorDeProcesos {
    static Scanner sc = new Scanner(System.in);
    static Recurso recurso = new Recurso();
    static Planificador planificador;
    static int tiempoGlobal = 0;
    static final String[] MENSAJES_PREDEFINIDOS = {
        "Solicitar recurso",
        "Liberar recurso",
        "Prioridad aumentada",
        "Prioridad disminuida",
        "Ejecutar tarea de I/O",
        "Terminar ejecución"
    };

    // Método principal para iniciar el simulador
    public static void main(String[] args) {
        System.out.println("====== SIMULADOR DE GESTOR DE PROCESOS ======");
        System.out.println("\nSeleccione un algoritmo de planificación:");
        System.out.println("1. FCFS (First Come First Served)");
        System.out.println("2. SJF (Shortest Job First)");
        System.out.println("3. Round Robin");
        System.out.println("4. Prioridad");
        
        int opcion = leerEnteroConRango("Seleccione una opción: ", 1, 4);
        String algoritmo = "";
        int quantum = 2; 
        
        switch (opcion) {
            case 1: algoritmo = "FCFS"; break;
            case 2: algoritmo = "SJF"; break;
            case 3: 
                algoritmo = "RoundRobin"; 
                quantum = leerEnteroConRango("Quantum para Round Robin (en unidades): ", 1, 10);
                break;
            case 4: algoritmo = "Prioridad"; break;
        }
        
        planificador = new Planificador(algoritmo, quantum);
        Log.registrar("SISTEMA", "Simulador iniciado con algoritmo " + algoritmo);
        
        System.out.println("\n====== MODO DE EJECUCIÓN ======");
        System.out.println("1. Modo Manual (Ir al menú principal)");
        System.out.println("2. Modo Automático (Generar simulación con datos aleatorios)");
        
        int modoEjecucion = leerEnteroConRango("Seleccione modo de ejecución: ", 1, 2);
        
        if (modoEjecucion == 1) {
            mostrarMenuPrincipal();
        } else {
            ejecutarSimulacionAutomatica();
        }
    }

    // Método para mostrar el menú principal
    static void mostrarMenuPrincipal() {
        while (true) {
            System.out.println("\n====== MENÚ PRINCIPAL ======");
            System.out.println("1. Crear Proceso");
            System.out.println("2. Listar Procesos");
            System.out.println("3. Estado de Recursos");
            System.out.println("4. Estado de Colas de Planificación");
            System.out.println("5. Ejecutar Proceso");
            System.out.println("6. Suspender/Continuar Proceso");
            System.out.println("7. Terminar Proceso");
            System.out.println("8. Enviar mensaje entre procesos");
            System.out.println("9. Leer mensajes de un proceso");
            System.out.println("10. Demostración Productor-Consumidor");
            System.out.println("11. Mostrar Procesos Bloqueados");
            System.out.println("12. Salir");
            
            int op = leerEnteroConRango("Seleccione una opción: ", 1, 12);
            switch (op) {
                case 1: crearProceso(); break;
                case 2: planificador.mostrarProcesos(); break;
                case 3: mostrarEstadoRecursos(); break;
                case 4: planificador.mostrarEstadoColas(); break;
                case 5: ejecutarProceso(); break;
                case 6: suspenderContinuar(); break;
                case 7: terminarProceso(); break;
                case 8: enviarMensaje(); break;
                case 9: leerMensajes(); break;
                case 10: ejecutarProdCons(); break;
                case 11: mostrarProcesosBloqueados(); break;
                case 12: 
                    Log.registrar("SISTEMA", "Finalizando simulador");
                    return;
            }
            
            planificador.actualizarColas();
        }
    }

    // Método para ejecutar una simulación automática
    static void ejecutarSimulacionAutomatica() {
        System.out.println("\n====== SIMULACIÓN AUTOMÁTICA ======");
        Log.registrar("SISTEMA", "Iniciando simulación automática con algoritmo " + planificador.algoritmo);
        
        Random random = new Random();
        int numProcesos = random.nextInt(11) + 5;
        
        System.out.println("\n=== Generando " + numProcesos + " procesos aleatorios ===");
        
        for (int i = 0; i < numProcesos; i++) {
            int prioridad = random.nextInt(10) + 1;
            int tiempoEjecucion = random.nextInt(20) + 1;
            PCB p = new PCB(prioridad, tiempoEjecucion);
            planificador.agregarProceso(p);
            
            if (random.nextBoolean()) {
                int memoriaRequerida = random.nextInt(1000) + 100;
                if (!recurso.solicitar(p, memoriaRequerida)) {
                    Log.registrar("RECURSO", "No se pudieron asignar recursos al PID " + p.pid);
                }
            }
            
            Log.registrar("PROCESO", "Proceso creado: PID " + p.pid + 
                         ", Prioridad " + p.prioridad + 
                         ", Tiempo " + p.tiempoEjecucion);
        }
        
        System.out.println("\n=== Estado inicial de procesos ===");
        planificador.mostrarProcesos();
        
        System.out.println("\n=== Estado inicial de recursos ===");
        mostrarEstadoRecursos();
        
        System.out.println("\n=== Estado inicial de colas ===");
        planificador.mostrarEstadoColas();
        
        System.out.println("\n=== Enviando mensajes aleatorios entre procesos ===");
        int numMensajes = random.nextInt(5) + 2;
        for (int i = 0; i < numMensajes; i++) {
            if (planificador.listaProcesos.size() >= 2) {
                int indiceOrigen = random.nextInt(planificador.listaProcesos.size());
                int indiceDestino = random.nextInt(planificador.listaProcesos.size());
                
                while (indiceOrigen == indiceDestino) {
                    indiceDestino = random.nextInt(planificador.listaProcesos.size());
                }
                
                PCB origen = planificador.listaProcesos.get(indiceOrigen);
                PCB destino = planificador.listaProcesos.get(indiceDestino);
                
                String mensaje = MENSAJES_PREDEFINIDOS[random.nextInt(MENSAJES_PREDEFINIDOS.length)];
                origen.enviarMensaje(destino.pid, mensaje);
            }
        }
        
        if (random.nextBoolean()) {
            System.out.println("\n=== Simulando situación de interbloqueo ===");
            simularInterbloqueo();
        }
        
        System.out.println("\n=== Ejecutando procesos con algoritmo " + planificador.algoritmo + " ===");
        ejecutarProceso();
        
        System.out.println("\n=== Estado final de procesos ===");
        planificador.mostrarProcesos();
        
        System.out.println("\n=== Estado final de recursos ===");
        mostrarEstadoRecursos();
        
        System.out.println("\n====== FIN DE LA SIMULACIÓN AUTOMÁTICA ======");
        System.out.println("\nPresione Enter para salir...");
        sc.nextLine();
        Log.registrar("SISTEMA", "Finalizando simulador");
    }

    // Método para simular una situación de interbloqueo
    static void simularInterbloqueo() {
        PCB p1 = new PCB(1, 10);
        PCB p2 = new PCB(2, 15);
        
        planificador.agregarProceso(p1);
        planificador.agregarProceso(p2);
        
        if (recurso.solicitar(p1, 200)) {
            Log.registrar("INTERBLOQUEO", "PID " + p1.pid + " obtuvo CPU y memoria");
            
            if (!recurso.solicitar(p2, 300)) {
                Log.registrar("INTERBLOQUEO", "PID " + p2.pid + " bloqueado esperando CPU");
            }
            
            recurso.liberar(p1);
        }
    }

    // Método para leer un entero dentro de un rango específico
    static int leerEnteroConRango(String mensaje, int min, int max) {
        while (true) {
            System.out.print(mensaje);
            try {
                int valor = sc.nextInt(); sc.nextLine();
                if (valor >= min && valor <= max) {
                    return valor;
                } else {
                    System.out.println("Por favor ingresa un valor entre " + min + " y " + max);
                }
            } catch (InputMismatchException e) {
                System.out.println("Por favor ingresa un número válido");
                sc.nextLine();
            }
        }
    }

    // Método para crear un nuevo proceso
    static void crearProceso() {
        int prioridad = leerEnteroConRango("Prioridad (1-10, siendo 1 la más alta): ", 1, 10);
        int tiempo = leerEnteroConRango("Tiempo de ejecución (unidades): ", 1, 60);
        PCB p = new PCB(prioridad, tiempo);
        planificador.agregarProceso(p);
        Log.registrar("PROCESO", "Proceso creado: PID " + p.pid + ", Prioridad " + p.prioridad + ", Tiempo " + p.tiempoEjecucion);
    }

    // Método para ejecutar procesos según el algoritmo de planificación
    static void ejecutarProceso() {
        List<PCB> procesosFinalizados = new ArrayList<>();
        List<PCB> procesosListos = new ArrayList<>();
        
        for (PCB p : planificador.listaProcesos) {
            if (p.estado.equals("Listo")) {
                procesosListos.add(p);
            }
        }
        
        if (procesosListos.isEmpty()) {
            Log.registrar("ERROR", "No hay procesos disponibles para ejecutar");
            return;
        }
        
        int tiempoInicio = tiempoGlobal;
        
        switch (planificador.algoritmo) {
            case "FCFS":
                procesosListos.sort(Comparator.comparingInt(p -> p.tiempoLlegada));
                break;
            case "SJF":
                procesosListos.sort(Comparator.comparingInt(p -> p.tiempoEjecucion));
                break;
            case "Prioridad":
                procesosListos.sort(Comparator.comparingInt(p -> p.prioridad));
                break;
            case "RoundRobin":
                Queue<PCB> colaTemp = new LinkedList<>();
                for (PCB p : procesosListos) {
                    colaTemp.offer(p);
                }
                procesosListos.clear();
                
                int ciclosRR = 0;
                while (!colaTemp.isEmpty() && ciclosRR < 100) {
                    PCB p = colaTemp.poll();
                    
                    int tiempoEjecucion = Math.min(p.tiempoEjecucion, planificador.quantum);
                    
                    for (PCB esperando : colaTemp) {
                        esperando.tiempoEspera += tiempoEjecucion;
                    }
                    
                    tiempoGlobal += tiempoEjecucion;
                    p.tiempoEjecucion -= tiempoEjecucion;
                    
                    if (p.tiempoEjecucion <= 0) {
                        p.tiempoFinalizacion = tiempoGlobal;
                        p.tiempoRetorno = p.tiempoFinalizacion - p.tiempoLlegada;
                        p.estado = "Terminado";
                        p.causaTerminacion = CausaTerminacion.NORMAL;
                        procesosFinalizados.add(p);
                    } else {
                        colaTemp.offer(p);
                    }
                    
                    ciclosRR++;
                }
                
                break;
        }
        
        if (!planificador.algoritmo.equals("RoundRobin")) {
            for (PCB p : procesosListos) {
                p.tiempoEspera = tiempoInicio - p.tiempoLlegada;
                
                tiempoGlobal += p.tiempoEjecucion;
                
                p.tiempoFinalizacion = tiempoGlobal;
                p.tiempoRetorno = p.tiempoFinalizacion - p.tiempoLlegada;
                
                p.estado = "Terminado";
                p.causaTerminacion = CausaTerminacion.NORMAL;
                
                procesosFinalizados.add(p);
                
                for (PCB espera : procesosListos) {
                    if (espera.pid != p.pid && !espera.estado.equals("Terminado")) {
                        espera.tiempoEspera += p.tiempoEjecucion;
                    }
                }
            }
        }
        
        System.out.println("\n┌" + "─".repeat(105) + "┐");
        System.out.println("│" + String.format("%-103s", " RESULTADOS DE EJECUCIÓN - ALGORITMO: " + planificador.algoritmo) + "  │");
        System.out.println("├" + "─".repeat(105) + "┤");
        
        System.out.println("│ " + String.format("%-6s %-10s %-10s %-10s %-20s %-20s %-21s", 
                "PID", "Llegada", "Ciclos", "Prioridad", "Tiempo Finalización", "Tiempo Espera", "Tiempo Retorno") + "│");
        System.out.println("├" + "─".repeat(105) + "┤");
        
        int totalEspera = 0;
        int totalRetorno = 0;
        
        for (PCB p : procesosFinalizados) {
            System.out.println("│ " + String.format("%-6d %-10d %-10d %-10d %-20d %-20d %-21d", 
                    p.pid, p.tiempoLlegada, p.tiempoOriginal, p.prioridad, 
                    p.tiempoFinalizacion, p.tiempoEspera, p.tiempoRetorno) + "│");
                    
            totalEspera += p.tiempoEspera;
            totalRetorno += p.tiempoRetorno;
        }
        
        System.out.println("├" + "─".repeat(105) + "┤");
        
        double promedioEspera = procesosFinalizados.isEmpty() ? 0 : (double) totalEspera / procesosFinalizados.size();
        double promedioRetorno = procesosFinalizados.isEmpty() ? 0 : (double) totalRetorno / procesosFinalizados.size();
        
        System.out.println("│ " + String.format("%-103s", " RESUMEN DE TIEMPOS") + "  │");
        System.out.println("│ " + String.format("%-103s", " Tiempo de espera promedio: " + String.format("%.2f", promedioEspera) + " unidades") + "  │");
        System.out.println("│ " + String.format("%-103s", " Tiempo de retorno promedio: " + String.format("%.2f", promedioRetorno) + " unidades") + "  │");
        System.out.println("└" + "─".repeat(105) + "┘");
        
        Log.registrar("PLANIFICADOR", "Ejecución completada para " + procesosFinalizados.size() + " procesos con algoritmo " + planificador.algoritmo);
    }

    // Método para suspender o continuar un proceso
    static void suspenderContinuar() {
        int pid = leerEnteroConRango("PID a suspender/reanudar: ", 1, Integer.MAX_VALUE);
        for (PCB p : planificador.listaProcesos) {
            if (p.pid == pid) {
                if (p.estado.equals("Listo")) {
                    p.estado = "Suspendido";
                    Log.registrar("PROCESO", "Proceso " + p.pid + " suspendido");
                } else if (p.estado.equals("Suspendido")) {
                    p.estado = "Listo";
                    Log.registrar("PROCESO", "Proceso " + p.pid + " reanudado");
                } else if (p.estado.equals("Bloqueado")) {
                    Log.registrar("ERROR", "No se puede suspender un proceso bloqueado");
                } else {
                    Log.registrar("ERROR", "No se puede suspender/reanudar proceso en estado: " + p.estado);
                }
                return;
            }
        }
        Log.registrar("ERROR", "PID " + pid + " no encontrado");
    }

    // Método para terminar un proceso
    static void terminarProceso() {
        int pid = leerEnteroConRango("PID a terminar: ", 1, Integer.MAX_VALUE);
        for (PCB p : planificador.listaProcesos) {
            if (p.pid == pid) {
                recurso.liberar(p);
                p.estado = "Terminado";
                p.causaTerminacion = CausaTerminacion.USUARIO;
                Log.registrar("PROCESO", "Proceso " + p.pid + " terminado por usuario");
                return;
            }
        }
        Log.registrar("ERROR", "PID " + pid + " no encontrado");
    }

    // Método para mostrar el estado de los recursos
    static void mostrarEstadoRecursos() {
        System.out.println("\n┌" + "─".repeat(70) + "┐");
        System.out.println("│" + String.format("%-68s", " ESTADO DE RECURSOS") + "  │");
        System.out.println("├" + "─".repeat(70) + "┤");
        System.out.println("│ Memoria disponible: " + String.format("%-27d", recurso.memoriaDisponible) + "MB│");
        System.out.println("│ CPU disponible:     " + String.format("%-27s", recurso.cpuDisponible ? "Sí" : "No") + "  │");
        
        if (!recurso.memoriaAsignadaPorProceso.isEmpty()) {
            System.out.println("├" + "─".repeat(70) + "┤");
            System.out.println("│ " + String.format("%-68s", " MEMORIA ASIGNADA A PROCESOS:") + "  │");
            for (Map.Entry<Integer, Integer> entry : recurso.memoriaAsignadaPorProceso.entrySet()) {
                System.out.println("│ " + String.format("%-68s", " PID " + entry.getKey() + ": " + entry.getValue() + "MB") + "  │");
            }
        }
        
        if (!recurso.recursosEsperados.isEmpty()) {
            System.out.println("├" + "─".repeat(70) + "┤");
            System.out.println("│ " + String.format("%-68s", " PROCESOS ESPERANDO RECURSOS:") + "  │");
            
            for (Map.Entry<Integer, List<String>> entry : recurso.recursosEsperados.entrySet()) {
                String recursos = String.join(", ", entry.getValue());
                if (recursos.length() > 50) recursos = recursos.substring(0, 47) + "...";
                System.out.println("│ " + String.format("%-68s", " PID " + entry.getKey() + ": " + recursos) + "  │");
            }
        }
        
        System.out.println("└" + "─".repeat(70) + "┘");
    }
    
    // Método para mostrar los procesos bloqueados
    static void mostrarProcesosBloqueados() {
        System.out.println("\n┌" + "─".repeat(70) + "┐");
        System.out.println("│" + String.format("%-68s", " PROCESOS BLOQUEADOS") + "  │");
        System.out.println("├" + "─".repeat(70) + "┤");
        
        boolean hayBloqueados = false;
        for (PCB p : planificador.listaProcesos) {
            if (p.estado.equals("Bloqueado")) {
                hayBloqueados = true;
                String recursosEsperados = String.join(", ", p.recursosEsperados);
                if (recursosEsperados.length() > 50) recursosEsperados = recursosEsperados.substring(0, 47) + "...";
                System.out.println("│ " + String.format("%-68s", " PID " + p.pid + ": esperando " + recursosEsperados) + "  │");
            }
        }
        
        if (!hayBloqueados) {
            System.out.println("│" + String.format("%-68s", " No hay procesos bloqueados") + "  │");
        }
        System.out.println("└" + "─".repeat(70) + "┘");
    }

    // Método para enviar un mensaje entre procesos
    static void enviarMensaje() {
        int pidOrigen = leerEnteroConRango("PID del proceso remitente: ", 1, Integer.MAX_VALUE);
        int pidDestino = leerEnteroConRango("PID del proceso destinatario: ", 1, Integer.MAX_VALUE);
        
        System.out.println("\nSeleccione un mensaje o escriba uno personalizado:");
        System.out.println("0. Mensaje personalizado");
        for (int i = 0; i < MENSAJES_PREDEFINIDOS.length; i++) {
            System.out.println((i+1) + ". " + MENSAJES_PREDEFINIDOS[i]);
        }
        
        int opMensaje = leerEnteroConRango("Opción: ", 0, MENSAJES_PREDEFINIDOS.length);
        String mensaje;
        
        if (opMensaje == 0) {
            System.out.print("Escriba su mensaje: ");
            mensaje = sc.nextLine();
        } else {
            mensaje = MENSAJES_PREDEFINIDOS[opMensaje-1];
        }
        
        PCB origen = null;
        for (PCB p : planificador.listaProcesos) {
            if (p.pid == pidOrigen) {
                origen = p;
                break;
            }
        }
        
        if (origen != null) {
            origen.enviarMensaje(pidDestino, mensaje);
        } else {
            Log.registrar("ERROR", "PID de origen " + pidOrigen + " no encontrado");
        }
    }

    // Método para leer los mensajes de un proceso
    static void leerMensajes() {
        int pid = leerEnteroConRango("PID del proceso para leer mensajes: ", 1, Integer.MAX_VALUE);
        
        for (PCB p : planificador.listaProcesos) {
            if (p.pid == pid) {
                p.leerMensajes();
                return;
            }
        }
        Log.registrar("ERROR", "PID " + pid + " no encontrado");
    }

    // Método para ejecutar la demostración del problema productor-consumidor
    static void ejecutarProdCons() {
        Buffer buffer = new Buffer();
        Log.registrar("DEMO", "Iniciando demostración Productor-Consumidor");

        System.out.println("\n¿Cómo quieres generar los valores para el productor?");
        System.out.println("1. Asignar manualmente");
        System.out.println("2. Generar aleatoriamente (1-10)");

        int opcion = leerEnteroConRango("Seleccione una opción: ", 1, 2);

        Thread productorThread;
        Thread consumidorThread;

        if (opcion == 1) {
            productorThread = new Thread(() -> {
                try {
                    for (int i = 1; i <= 10; i++) {
                        System.out.print("Hilo Productor: ");
                        int valor = leerEnteroConRango("Ingrese valor #" + i + " para producir (1-10): ", 1, 10);
                        buffer.producir(valor);
                        Thread.sleep(250);
                    }
                    Log.registrar("DEMO", "Productor completado");
                } catch (InterruptedException e) {
                    Log.registrar("ERROR", "Productor interrumpido: " + e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    Log.registrar("ERROR", "Error en productor: " + e.getMessage());
                }
            });
        } else {
            productorThread = new Thread(() -> {
                try {
                    Random random = new Random();
                    for (int i = 1; i <= 10; i++) {
                        int valor = random.nextInt(10) + 1;
                        buffer.producir(valor);
                        Thread.sleep(250);
                    }
                    Log.registrar("DEMO", "Productor completado");
                } catch (InterruptedException e) {
                    Log.registrar("ERROR", "Productor interrumpido: " + e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    Log.registrar("ERROR", "Error en productor: " + e.getMessage());
                }
            });
        }

        consumidorThread = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    buffer.consumir();
                    Thread.sleep(400);
                }
                Log.registrar("DEMO", "Consumidor completado");
            } catch (InterruptedException e) {
                Log.registrar("ERROR", "Consumidor interrumpido: " + e.getMessage());
                Thread.currentThread().interrupt(); 
            } catch (Exception e) {
                Log.registrar("ERROR", "Error en consumidor: " + e.getMessage());
            }
        });

        // Registra el inicio de la demostración y lanza los hilos
        Log.registrar("DEMO", "Demostración iniciada en hilos separados. Esperando finalización...");
        productorThread.start();
        consumidorThread.start();
        
        // Espera a que ambos hilos terminen antes de continuar
        try {
            productorThread.join(); 
            consumidorThread.join();
            Log.registrar("DEMO", "Demostración Productor-Consumidor finalizada.");
        } catch (InterruptedException e) {
            Log.registrar("ERROR", "Hilo principal interrumpido mientras esperaba a productor/consumidor: " + e.getMessage());
            Thread.currentThread().interrupt();
            productorThread.interrupt();
            consumidorThread.interrupt();
        }
    }
}
