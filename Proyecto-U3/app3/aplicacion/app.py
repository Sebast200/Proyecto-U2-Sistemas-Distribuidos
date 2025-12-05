import tkinter as tk
from tkinter import ttk, messagebox
import requests
import os
import threading
from datetime import datetime

MIDDLEWARE_URL = os.getenv("MIDDLEWARE_URL", "http://localhost:4000")

class DashboardApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Sistema de Gestión Distribuido - Casa Matriz")
        self.root.geometry("1100x750") 
        
        style = ttk.Style()
        style.theme_use('clam')
        style.configure("Treeview", rowheight=28, font=('Arial', 10))
        style.configure("Treeview.Heading", font=('Arial', 10, 'bold'))
        style.configure("TNotebook.Tab", padding=[12, 5], font=('Arial', 11, 'bold'))

        self.status_frame = tk.Frame(root, bg="#333", height=40)
        self.status_frame.pack(side=tk.TOP, fill=tk.X)
        
        self.indicators = {}
        self.create_status_indicator("Middleware/MySQL", "middleware")
        self.create_status_indicator("App 1 (Compras)", "app1")
        self.create_status_indicator("App 2 (Hospital)", "hospital")

        self.footer_frame = tk.Frame(root, bg="#007bff", height=30)
        self.footer_frame.pack(side=tk.BOTTOM, fill=tk.X)
        
        self.lbl_sync_status = tk.Label(self.footer_frame, text="Iniciando sincronización automática...", 
                                        bg="#007bff", fg="white", font=("Arial", 9, "bold"))
        self.lbl_sync_status.pack(side=tk.LEFT, padx=10)

        self.nav_frame = tk.Frame(root, bg="#f0f0f0", width=230)
        self.nav_frame.pack(side=tk.LEFT, fill=tk.Y)
        
        tk.Label(self.nav_frame, text="MENÚ", bg="#f0f0f0", font=("Arial", 14, "bold")).pack(pady=20)
        ttk.Button(self.nav_frame, text="Inicio", command=self.show_welcome_view).pack(fill=tk.X, padx=10, pady=5)
        
        tk.Label(self.nav_frame, text="Vistas Remotas:", bg="#f0f0f0", fg="gray").pack(anchor="w", padx=10, pady=(15, 5))
        ttk.Button(self.nav_frame, text="App 1: Compras", command=self.show_app1_lists_view).pack(fill=tk.X, padx=10, pady=5)
        ttk.Button(self.nav_frame, text="App 2: Hospital", command=self.show_hospital_view).pack(fill=tk.X, padx=10, pady=5)

        tk.Label(self.nav_frame, text="Almacén de Datos (Local):", bg="#f0f0f0", fg="gray").pack(anchor="w", padx=10, pady=(15, 5))
        ttk.Button(self.nav_frame, text="Ver Base de Datos Local", command=self.show_local_db_view).pack(fill=tk.X, padx=10, pady=5)

        self.content_frame = tk.Frame(root, bg="white")
        self.content_frame.pack(side=tk.RIGHT, expand=True, fill=tk.BOTH)

        self.show_welcome_view()
        self.start_status_check_loop()
        self.start_auto_sync_loop()

    def create_status_indicator(self, label, key):
        frame = tk.Frame(self.status_frame, bg="#333")
        frame.pack(side=tk.LEFT, padx=15)
        canvas = tk.Canvas(frame, width=12, height=12, bg="#333", highlightthickness=0)
        canvas.pack(side=tk.LEFT)
        circle = canvas.create_oval(2, 2, 10, 10, fill="gray")
        tk.Label(frame, text=label, fg="white", bg="#333", font=("Arial", 9)).pack(side=tk.LEFT, padx=5)
        self.indicators[key] = {'canvas': canvas, 'circle': circle}

    def update_indicator(self, key, status):
        if key not in self.indicators: return
        color = "#00ff00" if status == "up" else "#ff3333"
        self.indicators[key]['canvas'].itemconfig(self.indicators[key]['circle'], fill=color)

    def start_status_check_loop(self):
        def _check():
            try:
                r = requests.get(f"{MIDDLEWARE_URL}/api/system-status", timeout=2)
                data = r.json()
                self.root.after(0, lambda: self.update_indicator("middleware", data.get("middleware", "down")))
                self.root.after(0, lambda: self.update_indicator("app1", data.get("app1", "down")))
                self.root.after(0, lambda: self.update_indicator("hospital", data.get("hospital", "down")))

                try:
                    r_health = requests.get(f"{MIDDLEWARE_URL}/health", timeout=1)
                    master_name = r_health.json().get('master_actual', 'Desconocido')
                    if hasattr(self, 'lbl_local_db_title') and self.lbl_local_db_title.winfo_exists():
                        self.root.after(0, lambda: self.lbl_local_db_title.config(text=f"DB App3 - [{master_name}]"))
                except: pass

            except: pass 
        
        threading.Thread(target=_check, daemon=True).start()
        self.root.after(5000, self.start_status_check_loop)

    def start_auto_sync_loop(self):
        def _silent_sync():
            try:
                requests.post(f"{MIDDLEWARE_URL}/api/sync/app1", timeout=5)
                requests.post(f"{MIDDLEWARE_URL}/api/sync/hospital", timeout=5)
                
                hora = datetime.now().strftime("%H:%M:%S")
                self.root.after(0, lambda: self.lbl_sync_status.config(text=f"Sincronizado: {hora}", bg="#28a745"))
                self.root.after(0, self.refresh_local_view_if_active)
            except Exception as e:
                self.root.after(0, lambda: self.lbl_sync_status.config(text="Reconectando...", bg="#dc3545"))

        threading.Thread(target=_silent_sync, daemon=True).start()
        self.root.after(10000, self.start_auto_sync_loop)

    def refresh_local_view_if_active(self):
        if hasattr(self, 'tree_local_lists') and self.tree_local_lists.winfo_exists():
             self.load_local_data_all()

    def clear_content(self):
        for widget in self.content_frame.winfo_children(): widget.destroy()
        if hasattr(self, 'tree_local_lists'): del self.tree_local_lists
        if hasattr(self, 'lbl_local_db_title'): del self.lbl_local_db_title

    def show_welcome_view(self):
        self.clear_content()
        f = tk.Frame(self.content_frame, bg="white")
        f.place(relx=0.5, rely=0.5, anchor="center")
        tk.Label(f, text="Bienvenido a la Casa Matriz", font=("Arial", 24, "bold"), bg="white", fg="#333").pack(pady=10)
        tk.Label(f, text="Sistema de Gestión Distribuido", font=("Arial", 14), bg="white", fg="gray").pack(pady=5)

    def show_app1_lists_view(self):
        self.clear_content()
        header = tk.Frame(self.content_frame, bg="white", padx=20, pady=10); header.pack(fill=tk.X)
        tk.Label(header, text="Listas de Compras", font=("Arial", 16), bg="white").pack(side=tk.LEFT)
        tk.Button(header, text="↻ Recargar", command=self.load_lists_data, bg="#007bff", fg="white").pack(side=tk.RIGHT)

        table_frame = tk.Frame(self.content_frame, bg="white", padx=20); table_frame.pack(fill=tk.BOTH, expand=True)
        self.tree = ttk.Treeview(table_frame, columns=("id", "nombre"), show="headings")
        self.tree.heading("id", text="ID"); self.tree.column("id", width=50, anchor="center")
        self.tree.heading("nombre", text="Nombre de la Lista"); self.tree.column("nombre", width=400)
        self.tree.pack(fill=tk.BOTH, expand=True, pady=5)
        self.tree.bind("<Double-1>", self.on_list_double_click)
        threading.Thread(target=self.load_lists_data, daemon=True).start()

    def load_lists_data(self):
        if hasattr(self, 'tree'):
            for i in self.tree.get_children(): self.tree.delete(i)
        try:
            data = requests.get(f"{MIDDLEWARE_URL}/api/externo/app1/lists").json()
            for d in data: self.tree.insert("", tk.END, values=(d['id'], d['name']))
        except: pass

    def on_list_double_click(self, event):
        sel = self.tree.selection()
        if not sel: return
        self.show_app1_items_view(self.tree.item(sel[0], "values")[0], self.tree.item(sel[0], "values")[1])

    def show_app1_items_view(self, list_id, list_name):
        self.clear_content()
        header = tk.Frame(self.content_frame, bg="white", padx=20, pady=10); header.pack(fill=tk.X)
        tk.Button(header, text="⬅ Volver", command=self.show_app1_lists_view, bg="#6c757d", fg="white").pack(side=tk.LEFT, padx=(0, 10))
        tk.Label(header, text=f"{list_name}", font=("Arial", 16, "bold"), bg="white").pack(side=tk.LEFT)
        tk.Button(header, text="↻ Recargar Ítems", command=lambda: threading.Thread(target=lambda: self.load_items_data(list_id), daemon=True).start(), bg="#28a745", fg="white").pack(side=tk.RIGHT)

        table = tk.Frame(self.content_frame, bg="white", padx=20); table.pack(fill=tk.BOTH, expand=True)
        self.tree_items = ttk.Treeview(table, columns=("id", "desc"), show="headings")
        self.tree_items.heading("id", text="ID"); self.tree_items.column("id", width=50, anchor="center")
        self.tree_items.heading("desc", text="Producto"); self.tree_items.column("desc", width=400)
        self.tree_items.pack(fill=tk.BOTH, expand=True)
        threading.Thread(target=lambda: self.load_items_data(list_id), daemon=True).start()

    def load_items_data(self, list_id):
        if hasattr(self, 'tree_items'):
            for i in self.tree_items.get_children(): self.tree_items.delete(i)
        try:
            data = requests.get(f"{MIDDLEWARE_URL}/api/externo/app1/items", params={'list_id': list_id}).json()
            for d in data: self.tree_items.insert("", tk.END, values=(d['id'], d['description']))
        except: pass

    def show_hospital_view(self):
        self.clear_content()
        header = tk.Frame(self.content_frame, bg="white", padx=20, pady=10); header.pack(fill=tk.X)
        tk.Label(header, text="Gestión Hospitalaria", font=("Arial", 16), bg="white").pack(side=tk.LEFT)
        tk.Button(header, text="↻ Refrescar", command=self.load_hospital_data, bg="#17a2b8", fg="white").pack(side=tk.RIGHT)

        table = tk.Frame(self.content_frame, bg="white", padx=20); table.pack(fill=tk.BOTH, expand=True)
        self.tree_hospital = ttk.Treeview(table, columns=("id", "pac", "desc", "fecha"), show="headings")
        self.tree_hospital.heading("id", text="ID"); self.tree_hospital.column("id", width=40, anchor="center")
        self.tree_hospital.heading("pac", text="Paciente"); self.tree_hospital.column("pac", width=150)
        self.tree_hospital.heading("desc", text="Motivo"); self.tree_hospital.column("desc", width=250)
        self.tree_hospital.heading("fecha", text="Fecha"); self.tree_hospital.column("fecha", width=120, anchor="center")
        self.tree_hospital.pack(fill=tk.BOTH, expand=True, pady=5)

        threading.Thread(target=self.load_hospital_data, daemon=True).start()

    def load_hospital_data(self):
        if hasattr(self, 'tree_hospital'):
            for i in self.tree_hospital.get_children(): self.tree_hospital.delete(i)
        try:
            data = requests.get(f"{MIDDLEWARE_URL}/api/externo/hospital/citas").json()
            if isinstance(data, dict) and "error" in data: messagebox.showerror("Error", data["error"]); return
            for d in data: self.tree_hospital.insert("", tk.END, values=(d['id'], d['paciente'], d['descripcion'], d['fecha']))
        except Exception as e: messagebox.showerror("Error", str(e))

    def show_local_db_view(self):
        self.clear_content()
        header = tk.Frame(self.content_frame, bg="white", padx=20, pady=10); header.pack(fill=tk.X)
        self.lbl_local_db_title = tk.Label(header, text="DB App3 - [Buscando...]", 
                                           font=("Arial", 16, "bold"), bg="white", fg="#333")
        self.lbl_local_db_title.pack(side=tk.LEFT)

        notebook = ttk.Notebook(self.content_frame); notebook.pack(fill=tk.BOTH, expand=True, padx=20, pady=10)

        tab_c = tk.Frame(notebook, bg="white"); notebook.add(tab_c, text=" Datos de Compras ")
        tk.Label(tab_c, text="LISTAS", font=("Arial", 10, "bold"), bg="white").pack(anchor="w", pady=5)
        self.tree_local_lists = ttk.Treeview(tab_c, columns=("id", "name"), show="headings", height=5)
        self.tree_local_lists.heading("id", text="ID"); self.tree_local_lists.heading("name", text="Nombre")
        self.tree_local_lists.pack(fill=tk.X, padx=5)

        tk.Label(tab_c, text="PRODUCTOS", font=("Arial", 10, "bold"), bg="white").pack(anchor="w", pady=(10, 5))
        self.tree_local_items = ttk.Treeview(tab_c, columns=("id", "desc", "lid", "comp"), show="headings")
        self.tree_local_items.heading("id", text="ID"); self.tree_local_items.column("id", width=40)
        self.tree_local_items.heading("desc", text="Descripción")
        self.tree_local_items.heading("lid", text="ID Lista")
        self.tree_local_items.heading("comp", text="Completado")
        self.tree_local_items.pack(fill=tk.BOTH, expand=True, padx=5, pady=(0, 10))


        tab_h = tk.Frame(notebook, bg="white"); notebook.add(tab_h, text=" Datos de Hospital ")
        tk.Label(tab_h, text="CITAS MÉDICAS", font=("Arial", 10, "bold"), bg="white").pack(anchor="w", pady=10)
        self.tree_local_citas = ttk.Treeview(tab_h, columns=("id", "pac", "desc", "fecha"), show="headings")
        self.tree_local_citas.heading("id", text="ID")
        self.tree_local_citas.heading("pac", text="Paciente")
        self.tree_local_citas.heading("desc", text="Motivo")
        self.tree_local_citas.heading("fecha", text="Fecha")
        self.tree_local_citas.pack(fill=tk.BOTH, expand=True, padx=5, pady=10)

        threading.Thread(target=self.load_local_data_all, daemon=True).start()

    def load_local_data_all(self):
        try:
            if hasattr(self, 'tree_local_lists'):
                for i in self.tree_local_lists.get_children(): self.tree_local_lists.delete(i)
                data = requests.get(f"{MIDDLEWARE_URL}/api/local/lists").json()
                for d in data: self.tree_local_lists.insert("", tk.END, values=(d['id'], d['name']))

            if hasattr(self, 'tree_local_items'):
                for i in self.tree_local_items.get_children(): self.tree_local_items.delete(i)
                data = requests.get(f"{MIDDLEWARE_URL}/api/local/items").json()
                for d in data: self.tree_local_items.insert("", tk.END, values=(d['id'], d['description'], d['list_id'], d['completed']))

            if hasattr(self, 'tree_local_citas'):
                for i in self.tree_local_citas.get_children(): self.tree_local_citas.delete(i)
                data = requests.get(f"{MIDDLEWARE_URL}/api/local/citas").json()
                for d in data: self.tree_local_citas.insert("", tk.END, values=(d['id'], d['paciente'], d['descripcion'], d['fecha']))
        except: pass

if __name__ == "__main__":
    root = tk.Tk()
    app = DashboardApp(root)
    root.mainloop()