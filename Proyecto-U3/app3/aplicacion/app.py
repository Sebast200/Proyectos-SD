import tkinter as tk
from tkinter import ttk, messagebox
import requests
import os
import threading

MIDDLEWARE_URL = os.getenv("MIDDLEWARE_URL", "http://localhost:4000")

class DashboardApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Sistema de Gestión Distribuido - Casa Matriz")
        self.root.geometry("900x600")
        
        style = ttk.Style()
        style.theme_use('clam')
        style.configure("Treeview", rowheight=30, font=('Arial', 10))
        style.configure("Treeview.Heading", font=('Arial', 10, 'bold'))

        self.status_frame = tk.Frame(root, bg="#333", height=50)
        self.status_frame.pack(side=tk.TOP, fill=tk.X)
        
        self.indicators = {}
        self.create_status_indicator("Middleware", "middleware")
        self.create_status_indicator("App 1 (Compras)", "app1")
        self.create_status_indicator("App 2 (Hospital)", "hospital")

        self.nav_frame = tk.Frame(root, bg="#f0f0f0", width=200)
        self.nav_frame.pack(side=tk.LEFT, fill=tk.Y)
        
        tk.Label(self.nav_frame, text="MENÚ", bg="#f0f0f0", font=("Arial", 14, "bold")).pack(pady=20)
        
        ttk.Button(self.nav_frame, text="Inicio", command=self.show_welcome_view).pack(fill=tk.X, padx=10, pady=5)
        ttk.Button(self.nav_frame, text="App 1: Compras", command=self.show_app1_lists_view).pack(fill=tk.X, padx=10, pady=5)
        ttk.Button(self.nav_frame, text="App 2: Hospital", command=self.show_hospital_view).pack(fill=tk.X, padx=10, pady=5)

        # --- SECCIÓN DERECHA: CONTENIDO ---
        self.content_frame = tk.Frame(root, bg="white")
        self.content_frame.pack(side=tk.RIGHT, expand=True, fill=tk.BOTH, padx=20, pady=20)

        self.show_welcome_view()
        self.start_auto_refresh()

    def create_status_indicator(self, label, key):
        frame = tk.Frame(self.status_frame, bg="#333")
        frame.pack(side=tk.LEFT, padx=15)
        canvas = tk.Canvas(frame, width=15, height=15, bg="#333", highlightthickness=0)
        canvas.pack(side=tk.LEFT)
        circle = canvas.create_oval(2, 2, 13, 13, fill="gray")
        tk.Label(frame, text=label, fg="white", bg="#333").pack(side=tk.LEFT, padx=5)
        self.indicators[key] = {'canvas': canvas, 'circle': circle}

    def update_indicator(self, key, status):
        color = "#00ff00" if status == "up" else "#ff3333"
        self.indicators[key]['canvas'].itemconfig(self.indicators[key]['circle'], fill=color)

    def start_auto_refresh(self):
        self.check_system_status()
        self.root.after(5000, self.start_auto_refresh)

    def check_system_status(self):
        def _check():
            try:
                r = requests.get(f"{MIDDLEWARE_URL}/api/system-status", timeout=2)
                data = r.json()
                self.root.after(0, lambda: self.update_indicator("middleware", data.get("middleware", "down")))
                self.root.after(0, lambda: self.update_indicator("app1", data.get("app1", "down")))
                self.root.after(0, lambda: self.update_indicator("hospital", data.get("hospital", "down")))
            except:
                pass 
        threading.Thread(target=_check, daemon=True).start()

    def clear_content(self):
        for widget in self.content_frame.winfo_children():
            widget.destroy()

    def show_welcome_view(self):
        self.clear_content()
        welcome_frame = tk.Frame(self.content_frame, bg="white")
        welcome_frame.place(relx=0.5, rely=0.5, anchor="center")
        tk.Label(welcome_frame, text="Bienvenido a la Casa Matriz", font=("Arial", 24, "bold"), bg="white", fg="#333").pack(pady=10)
        tk.Label(welcome_frame, text="Sistema de Gestión Distribuido", font=("Arial", 14), bg="white", fg="gray").pack(pady=5)
        tk.Label(welcome_frame, text="__________________________", bg="white", fg="#ddd").pack(pady=20)
        tk.Label(welcome_frame, text="Seleccione una aplicación en el menú lateral para comenzar.", font=("Arial", 11), bg="white").pack(pady=20)

    # ==========================================
    # LÓGICA DE APP 1 (LISTAS -> ITEMS)
    # ==========================================

    def show_app1_lists_view(self):
        self.clear_content()
        
        header_frame = tk.Frame(self.content_frame, bg="white")
        header_frame.pack(fill=tk.X, pady=(0, 10))

        tk.Label(header_frame, text="Listas de Compras (App 1)", font=("Arial", 16), bg="white").pack(side=tk.LEFT)
        tk.Button(header_frame, text="↻ Recargar Listas", command=self.load_lists_data, bg="#007bff", fg="white").pack(side=tk.RIGHT)

        columns = ("id", "nombre")
        self.tree = ttk.Treeview(self.content_frame, columns=columns, show="headings", height=15)
        self.tree.heading("id", text="ID")
        self.tree.heading("nombre", text="Nombre de la Lista")
        self.tree.column("id", width=50, anchor="center")
        self.tree.column("nombre", width=400)
        
        self.tree.pack(fill=tk.BOTH, expand=True, pady=10)
        self.tree.bind("<Double-1>", self.on_list_double_click)

        threading.Thread(target=self.load_lists_data, daemon=True).start()

    def load_lists_data(self):
        if hasattr(self, 'tree'):
            for item in self.tree.get_children():
                self.tree.delete(item)
        try:
            r = requests.get(f"{MIDDLEWARE_URL}/api/externo/app1/lists")
            data = r.json()
            for lista in data:
                self.tree.insert("", tk.END, values=(lista['id'], lista['name']))
        except Exception as e:
            messagebox.showerror("Error", f"No se pudieron cargar las listas: {e}")

    def on_list_double_click(self, event):
        selection = self.tree.selection()
        if not selection:
            return
        item_id = self.tree.item(selection[0], "values")[0]
        list_name = self.tree.item(selection[0], "values")[1]
        self.show_app1_items_view(item_id, list_name)

    def show_app1_items_view(self, list_id, list_name):
        self.clear_content()
        
        header_frame = tk.Frame(self.content_frame, bg="white")
        header_frame.pack(fill=tk.X, pady=(0, 10))
        
        tk.Button(header_frame, text="⬅ Volver", command=self.show_app1_lists_view, bg="#6c757d", fg="white").pack(side=tk.LEFT, padx=(0, 10))
        tk.Label(header_frame, text=f"Contenido: {list_name}", font=("Arial", 16, "bold"), bg="white").pack(side=tk.LEFT)
        tk.Button(header_frame, text="↻ Recargar Productos", command=lambda: threading.Thread(target=lambda: self.load_items_data(list_id), daemon=True).start(), bg="#28a745", fg="white").pack(side=tk.RIGHT)

        columns = ("id", "descripcion")
        self.tree_items = ttk.Treeview(self.content_frame, columns=columns, show="headings", height=15)
        self.tree_items.heading("id", text="ID")
        self.tree_items.heading("descripcion", text="Productos")
        self.tree_items.column("id", width=50, anchor="center")
        self.tree_items.column("descripcion", width=400)
        self.tree_items.pack(fill=tk.BOTH, expand=True)

        threading.Thread(target=lambda: self.load_items_data(list_id), daemon=True).start()

    def load_items_data(self, list_id):
        if hasattr(self, 'tree_items'):
            for item in self.tree_items.get_children():
                self.tree_items.delete(item)
        try:
            r = requests.get(f"{MIDDLEWARE_URL}/api/externo/app1/items", params={'list_id': list_id})
            data = r.json()
            for item in data:
                self.tree_items.insert("", tk.END, values=(item['id'], item['description']))
        except Exception as e:
            messagebox.showerror("Error", f"Error cargando items: {e}")

    # ==========================================
    # VISTA HOSPITAL (PACIENTES -> CITAS)
    # ==========================================
    def show_hospital_view(self):
        self.clear_content()
        
        header_frame = tk.Frame(self.content_frame, bg="white")
        header_frame.pack(fill=tk.X, pady=(0, 10))

        tk.Label(header_frame, text="Gestión Hospitalaria (App 2)", font=("Arial", 16), bg="white").pack(side=tk.LEFT)
        
        tk.Button(header_frame, text="↻ Refrescar Pacientes", command=self.load_hospital_data,
                  bg="#17a2b8", fg="white").pack(side=tk.RIGHT)

        columns = ("id", "paciente", "descripcion", "fecha")
        self.tree_hospital = ttk.Treeview(self.content_frame, columns=columns, show="headings", height=15)
        
        self.tree_hospital.heading("id", text="ID")
        self.tree_hospital.heading("paciente", text="Paciente")
        self.tree_hospital.heading("descripcion", text="Motivo / Descripción")
        self.tree_hospital.heading("fecha", text="Fecha Ingreso")
        
        self.tree_hospital.column("id", width=40, anchor="center")
        self.tree_hospital.column("paciente", width=150)
        self.tree_hospital.column("descripcion", width=250)
        self.tree_hospital.column("fecha", width=120, anchor="center")
        
        self.tree_hospital.pack(fill=tk.BOTH, expand=True, pady=10)

        threading.Thread(target=self.load_hospital_data, daemon=True).start()

    def load_hospital_data(self):
        if hasattr(self, 'tree_hospital'):
            for item in self.tree_hospital.get_children():
                self.tree_hospital.delete(item)

        try:
            r = requests.get(f"{MIDDLEWARE_URL}/api/externo/hospital/citas")
            data = r.json()
            
            # Verificamos si el middleware nos devolvió un error
            if isinstance(data, dict) and "error" in data:
                messagebox.showerror("Error Hospital", data["error"])
                return

            for cita in data:
                self.tree_hospital.insert("", tk.END, values=(
                    cita.get('id'), 
                    cita.get('paciente'), 
                    cita.get('descripcion'),
                    cita.get('fecha')
                ))
        except Exception as e:
            messagebox.showerror("Error", f"No se pudo conectar al Hospital.\n¿Reconstruiste el middleware?\n\nError: {e}")

if __name__ == "__main__":
    root = tk.Tk()
    app = DashboardApp(root)
    root.mainloop()