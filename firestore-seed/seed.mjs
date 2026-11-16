// =====================================================================
// Datos de prueba para Ruka Inventario (Firestore + Firebase Auth)
//
// Crea una empresa de prueba completa: usuarios con cada rol, 3 bodegas,
// 20 productos con stock, lotes con vencimientos, números de serie y
// tareas en todos los módulos (recepción, picking, despacho, conteo,
// ajustes, transferencias), más historial de movimientos.
//
// Uso (proyecto real):
//   1. Consola Firebase > Configuración del proyecto > Cuentas de servicio >
//      "Generar nueva clave privada". Guarda el archivo como
//      firestore-seed/service-account.json (está en .gitignore: NO lo subas).
//   2. cd firestore-seed && npm install && npm run seed
//
// Opciones:
//   --company=DEMO        id de la empresa (código que se ingresa en la app)
//   --password=Demo1234   contraseña de los usuarios de prueba
//   --key=ruta.json       otra ubicación de la clave de servicio
//
// Emulador local: npm run seed:emulator (no toca el proyecto real).
//
// Es idempotente: los documentos tienen id fijo, así que volver a correrlo
// deja los datos como recién cargados (no duplica). No borra nada fuera de
// esos ids. Los datos de otras empresas no se tocan.
// =====================================================================

import { readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { initializeApp, cert } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';

const here = dirname(fileURLToPath(import.meta.url));
const args = Object.fromEntries(process.argv.slice(2)
    .filter(a => a.startsWith('--'))
    .map(a => { const [k, ...v] = a.slice(2).split('='); return [k, v.join('=') || 'true']; }));

const C = (args.company || 'DEMO').trim();
const PASSWORD = args.password || 'Demo1234';
const EMULATOR = !!process.env.FIRESTORE_EMULATOR_HOST;

// ---------------------------------------------------------------------
// Conexión
// ---------------------------------------------------------------------

let app;
if (EMULATOR) {
    if (!process.env.FIREBASE_AUTH_EMULATOR_HOST) {
        console.error('Firestore apunta al emulador pero Auth no: inicia también el emulador de auth.');
        process.exit(1);
    }
    app =initializeApp({ projectId: process.env.GCLOUD_PROJECT || 'demo-ruka' });
} else {
    const keyPath = args.key || process.env.GOOGLE_APPLICATION_CREDENTIALS || join(here, 'service-account.json');
    if (!existsSync(keyPath)) {
        console.error(`No encuentro la clave de servicio en ${keyPath}.\n`
            + 'Descárgala en Consola Firebase > Configuración del proyecto > Cuentas de servicio > '
            + '"Generar nueva clave privada" y guárdala como firestore-seed/service-account.json.');
        process.exit(1);
    }
    const key = JSON.parse(readFileSync(keyPath, 'utf8'));
    app = initializeApp({ credential: cert(key), projectId: key.project_id });
}
const db = getFirestore(app);
const auth = getAuth(app);
const projectId = app.options.projectId;

// ---------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------

const DAY = 24 * 60 * 60 * 1000;
const now = Date.now();
/** Fecha relativa a hoy: days(-2) = hace 2 días, days(30) = en 30 días. */
const days = (d, hours = 0) => Timestamp.fromMillis(now + d * DAY + hours * 60 * 60 * 1000);
const pid = code => `${C}_${code}`;

const writer = db.bulkWriter();
let writes = 0;
writer.onWriteError(err => {
    console.error('Error escribiendo', err.documentRef.path, err.message);
    return false;
});
const put = (path, data) => { writes++; writer.set(db.doc(path), data); };

// ---------------------------------------------------------------------
// Empresa y bodegas
// ---------------------------------------------------------------------

const WH = {
    // lat/lng: los usa el mapa de bodegas (y el clima de Open-Meteo en esa ubicación).
    central: { id: `${C}_CENTRAL`, name: 'Bodega Central Concepción', lat: -36.8201, lng: -73.0444, address: 'Av. Los Carrera 1200, Concepción' },
    tal: { id: `${C}_TALCAHUANO`, name: 'Sucursal Talcahuano', lat: -36.7246, lng: -73.1172, address: 'Colón 450, Talcahuano' },
    chi: { id: `${C}_CHILLAN`, name: 'Sucursal Chillán', lat: -36.6066, lng: -72.1034, address: 'Av. Libertad 800, Chillán' },
};

// ---------------------------------------------------------------------
// Usuarios (uno por rol + un operario y un supervisor en sucursal)
// ---------------------------------------------------------------------

const USERS = {
    op: { email: 'operario.central@example.com', fullName: 'Camila Rojas', role: 'OPERARIO', wh: WH.central },
    opTal: { email: 'operario.talcahuano@example.com', fullName: 'Diego Muñoz', role: 'OPERARIO', wh: WH.tal },
    sup: { email: 'supervisor.central@example.com', fullName: 'Valentina Soto', role: 'SUPERVISOR', wh: WH.central },
    supTal: { email: 'supervisor.talcahuano@example.com', fullName: 'Javiera Pérez', role: 'SUPERVISOR', wh: WH.tal },
    jefe: { email: 'jefe.bodega@example.com', fullName: 'Andrés Fuentes', role: 'JEFE_BODEGA', wh: WH.central },
};

async function ensureAuthUser(u) {
    try {
        const existing = await auth.getUserByEmail(u.email);
        await auth.updateUser(existing.uid, { password: PASSWORD, displayName: u.fullName, disabled: false });
        return existing.uid;
    } catch (e) {
        if (e.code !== 'auth/user-not-found') throw e;
        const created = await auth.createUser({ email: u.email, password: PASSWORD, displayName: u.fullName });
        return created.uid;
    }
}

// ---------------------------------------------------------------------
// Catálogo: {código, nombre, EAN, ubicación, categoría, mínimo, stock [central, tal, chi]}
// PROD-101..108 son los mismos de DemoCatalog (mismos códigos de barras).
// ---------------------------------------------------------------------

const PRODUCTS = [
    ['PROD-101', 'Caja de Clavos de Acero 2.5"', '7801234567890', 'A-01-01', 'Fijaciones', 10, [60, 25, 15]],
    ['PROD-102', 'Martillo Carpintero 16 oz', '7801234567891', 'A-01-02', 'Herramientas', 5, [24, 8, 6]],
    ['PROD-103', 'Cinta Métrica 5 m', '7801234567892', 'A-02-01', 'Herramientas', 8, [40, 12, 10]],
    ['PROD-104', 'Destornillador Philips #2', '7801234567893', 'A-02-03', 'Herramientas', 6, [4, 10, 3]],
    ['PROD-105', 'Guantes de Seguridad Talla L', '7801234567894', 'B-01-01', 'Seguridad', 20, [120, 50, 30]],
    ['PROD-106', 'Casco de Seguridad Blanco', '7801234567895', 'B-01-02', 'Seguridad', 5, [18, 6, 2]],
    ['PROD-107', 'Taladro Percutor 750W', '7801234567896', 'C-01-01', 'Eléctricos', 2, [4, 2, 1], { serialized: true }],
    ['PROD-108', 'Brocas para Concreto (Set 5)', '7801234567897', 'C-02-02', 'Eléctricos', 4, [15, 9, 0]],
    ['PROD-109', 'Tornillos Volcanita 1" (caja 100)', '7801234567898', 'A-01-03', 'Fijaciones', 15, [80, 30, 20]],
    ['PROD-110', 'Tarugos Plásticos 8 mm (bolsa 50)', '7801234567899', 'A-01-04', 'Fijaciones', 10, [45, 20, 8]],
    ['PROD-111', 'Silicona Transparente 280 ml', '7801234567900', 'D-01-01', 'Químicos', 12, [36, 12, 10]],
    ['PROD-112', 'Adhesivo de Montaje 370 g', '7801234567901', 'D-01-02', 'Químicos', 10, [22, 8, 5]],
    ['PROD-113', 'Pintura Látex Blanco 1 gal', '7801234567902', 'D-02-01', 'Pinturas', 6, [14, 6, 4]],
    ['PROD-114', 'Esmalte Sintético Rojo 1/4 gal', '7801234567903', 'D-02-02', 'Pinturas', 4, [9, 3, 0]],
    ['PROD-115', 'Lentes de Seguridad Claros', '7801234567904', 'B-02-01', 'Seguridad', 15, [60, 25, 12]],
    ['PROD-116', 'Esmeril Angular 4 1/2" 850W', '7801234567905', 'C-01-02', 'Eléctricos', 2, [3, 1, 1], { serialized: true }],
    ['PROD-117', 'Sierra Circular 7 1/4" 1400W', '7801234567906', 'C-01-03', 'Eléctricos', 1, [2, 1, 0], { serialized: true }],
    ['PROD-118', 'Extensión Eléctrica 10 m', '7801234567907', 'C-03-01', 'Eléctricos', 5, [16, 6, 4]],
    ['PROD-119', 'Llave Ajustable 10"', '7801234567908', 'A-03-01', 'Herramientas', 4, [12, 5, 3]],
    ['PROD-120', 'Mascarilla N95 (caja 20)', '7801234567909', 'B-02-02', 'Seguridad', 10, [8, 10, 6]],
];
const P = Object.fromEntries(PRODUCTS.map(p => [p[0], {
    id: pid(p[0]), code: p[0], name: p[1], barcode: p[2], location: p[3], category: p[4],
    minStock: p[5], stock: p[6], serialized: !!(p[7] && p[7].serialized),
}]));

/** Línea de una subcolección ITEMS a partir del catálogo. */
const line = code => {
    const p = P[code];
    return { productId: p.id, productCode: p.code, productName: p.name, barcode: p.barcode, location: p.location };
};

// ---------------------------------------------------------------------
// Lotes: {producto: [[lote, días para vencer, central, tal, chi]]}
// La suma por bodega coincide con el stock del producto.
// ---------------------------------------------------------------------

const LOTS = {
    'PROD-111': [['L-SIL-2401', -20, 6, 0, 0], ['L-SIL-2402', 12, 10, 4, 0], ['L-SIL-2403', 200, 20, 8, 10]],
    'PROD-112': [['L-ADH-118', 5, 7, 0, 0], ['L-ADH-131', 150, 15, 8, 5]],
    'PROD-113': [['L-PIN-0925', 25, 4, 0, 0], ['L-PIN-1103', 400, 10, 6, 4]],
    'PROD-114': [['L-ESM-077', 60, 9, 3, 0]],
    'PROD-120': [['L-N95-A1', 3, 8, 0, 0], ['L-N95-B2', 300, 0, 10, 6]],
};
const whList = [WH.central, WH.tal, WH.chi];
const lotId = (wh, code, lot) => `${C}_${wh.id}_${pid(code)}_${lot}`.replace(/\//g, '-');

// ---------------------------------------------------------------------
// Series: {producto: {prefijo, central, tal, chi}} + casos especiales
// ---------------------------------------------------------------------

const SERIAL_PREFIX = { 'PROD-107': 'TP750', 'PROD-116': 'ESM850', 'PROD-117': 'SC1400' };
const serialName = (code, n) => `${SERIAL_PREFIX[code]}-${String(n).padStart(4, '0')}`;

// ---------------------------------------------------------------------
// Carga
// ---------------------------------------------------------------------

async function main() {
    console.log(`Proyecto: ${projectId}${EMULATOR ? ' (EMULADOR)' : ''}`);
    console.log(`Empresa:  ${C}\n`);

    // Usuarios
    const uid = {};
    for (const [key, u] of Object.entries(USERS)) {
        uid[key] = await ensureAuthUser(u);
        put(`USERS/${uid[key]}`, {
            email: u.email, fullName: u.fullName, role: u.role, companyId: C,
            warehouseId: u.wh.id, warehouseName: u.wh.name,
        });
    }
    const by = key => ({ id: uid[key], name: USERS[key].fullName });

    // Empresa y bodegas
    put(`COMPANIES/${C}`, { code: C, name: 'Ferretería Ruka (datos de prueba)' });
    for (const wh of whList) {
        put(`WAREHOUSES/${wh.id}`, { companyId: C, name: wh.name, lat: wh.lat, lng: wh.lng, address: wh.address });
    }

    // Productos
    for (const p of Object.values(P)) {
        put(`PRODUCTS/${p.id}`, {
            companyId: C, code: p.code, name: p.name, barcode: p.barcode, location: p.location,
            category: p.category, minStock: p.minStock, serialized: p.serialized,
            stock: { [WH.central.id]: p.stock[0], [WH.tal.id]: p.stock[1], [WH.chi.id]: p.stock[2] },
        });
    }

    // Lotes
    for (const [code, lots] of Object.entries(LOTS)) {
        const p = P[code];
        for (const [lot, expiresIn, ...qty] of lots) {
            qty.forEach((q, i) => {
                if (q <= 0) return;
                put(`LOTS/${lotId(whList[i], code, lot)}`, {
                    companyId: C, warehouseId: whList[i].id, productId: p.id, productCode: code,
                    productName: p.name, lotNumber: lot, expiryDate: days(expiresIn), quantity: q,
                    reference: 'Carga inicial', status: 'ACTIVO', createdByName: USERS.sup.fullName,
                    createdAt: days(-30),
                });
            });
        }
    }

    // Series en bodega (una por unidad de stock) + casos de otros estados
    const serial = (code, n, status, wh, reference, updatedBy = USERS.sup.fullName, when = days(-30)) => {
        const p = P[code];
        const sn = serialName(code, n);
        put(`SERIALS/${`${C}_${p.id}_${sn}`.replace(/\//g, '-')}`, {
            companyId: C, productId: p.id, productCode: code, productName: p.name, serialNumber: sn,
            status, warehouseId: wh.id, reference, receivedReference: 'Carga inicial',
            updatedByName: updatedBy, updatedAt: when,
        });
        return sn;
    };
    for (const code of Object.keys(SERIAL_PREFIX)) {
        let n = 1;
        P[code].stock.forEach((q, i) => {
            for (let k = 0; k < q; k++) serial(code, n++, 'EN_BODEGA', whList[i], 'Carga inicial');
        });
    }
    // Casos especiales (numeración después de las unidades en bodega)
    const snDispatched = serial('PROD-107', 8, 'DESPACHADO', WH.central, 'PK-2990', USERS.op.fullName, days(-4));
    const snInTransit = serial('PROD-107', 9, 'EN_TRANSITO', WH.central, 'TR-0103', USERS.opTal.fullName, days(0, -2));
    const snRetired = serial('PROD-116', 6, 'BAJA', WH.central, 'Ajuste aprobado', USERS.sup.fullName, days(-2));
    const snDamagedPending = serialName('PROD-116', 3); // en bodega central, con ajuste pendiente

    // ---------------- Recepciones (órdenes de compra) ----------------
    const receptions = [
        ['OC-4501', 'Distribuidora del Sur Ltda.', 'Hoy 10:00', 'SIN_INICIAR', '',
            [['PROD-101', 50, 0], ['PROD-105', 100, 0], ['PROD-107', 2, 0]]],
        ['OC-4502', 'Importadora Andes SpA', 'Hoy 14:30', 'PARCIAL', 'Entregar en andén 2',
            [['PROD-111', 24, 12], ['PROD-112', 20, 0], ['PROD-113', 10, 10]]],
        ['OC-4503', 'Ferreteros Unidos S.A.', 'Mañana 09:00', 'SIN_INICIAR', '',
            [['PROD-109', 60, 0], ['PROD-110', 40, 0], ['PROD-115', 30, 0], ['PROD-120', 10, 0]]],
        ['OC-4498', 'Herramientas Biobío Ltda.', 'Ayer', 'COMPLETA', 'Recibida sin observaciones',
            [['PROD-102', 10, 10], ['PROD-104', 12, 12]]],
    ];
    for (const [code, supplier, eta, status, notes, items] of receptions) {
        const id = `${C}_${code}`;
        put(`RECEPTIONS/${id}`, { companyId: C, code, supplierName: supplier, etaLabel: eta, status, notes, lineCount: items.length });
        items.forEach(([pc, ordered, received], i) => {
            const p = P[pc];
            put(`RECEPTIONS/${id}/ITEMS/L${i + 1}`, {
                productCode: pc, productName: p.name, barcode: p.barcode,
                orderQuantity: ordered, receivedQuantity: received,
            });
        });
    }

    // Discrepancias abiertas
    const discrepancies = [
        ['D1', 'OC-4502', 'PROD-111', 'FALTANTE', 2, 'Caja master llegó con 2 unidades menos', days(0, -3)],
        ['D2', 'OC-4498', 'PROD-104', 'DANADO', 1, 'Mango quebrado en el embalaje', days(-1)],
    ];
    for (const [k, rc, pc, type, qty, notes, when] of discrepancies) {
        put(`DISCREPANCIES/${C}_${k}`, {
            companyId: C, warehouseId: WH.central.id, receptionId: `${C}_${rc}`, receptionCode: rc,
            productCode: pc, productName: P[pc].name, type, quantity: qty, notes, evidenceId: '',
            status: 'ABIERTA', createdByName: USERS.op.fullName, createdAt: when,
        });
    }

    // ---------------- Picking / Despacho ----------------
    // [código, bodega, cliente, plazo, estado, asignado, [[producto, pedido, recolectado]], extra]
    const pickings = [
        ['PK-3001', WH.central, 'Ferretería Don Pedro', 'Salida hoy 15:00', 'PENDIENTE', null,
            [['PROD-105', 10, 0], ['PROD-101', 6, 0], ['PROD-109', 12, 0]]],
        ['PK-3002', WH.central, 'Constructora Biobío', 'Salida hoy 17:30', 'PENDIENTE', 'op',
            [['PROD-103', 4, 0], ['PROD-111', 6, 0], ['PROD-118', 2, 0]]],
        ['PK-3003', WH.central, 'Comercial Lota', 'Salida hoy 12:00', 'EN_PROCESO', 'op',
            [['PROD-102', 2, 2], ['PROD-119', 3, 1], ['PROD-115', 10, 0]]],
        ['PK-3004', WH.central, 'Maestranza San Pedro', 'Salida mañana', 'PARCIAL', 'op',
            [['PROD-113', 5, 3], ['PROD-106', 2, 2]]],
        ['PK-3005', WH.central, 'Inmobiliaria Andalué', 'Salida hoy 16:00', 'PREPARADO', 'op',
            [['PROD-107', 1, 1], ['PROD-108', 2, 2]]],
        ['PK-3006', WH.central, 'Ferretería El Roble', 'Salida hoy 18:00', 'PREPARADO', 'sup',
            [['PROD-105', 20, 20], ['PROD-115', 10, 10]]],
        ['PK-3007', WH.central, 'Constructora Penco', 'Salió ayer', 'DESPACHADO', 'op',
            [['PROD-101', 10, 10], ['PROD-103', 2, 2]],
            { guideNumber: 'GD-000451', carrier: 'Transportes Biobío', plate: 'KJTR45', packages: 3, dispatchedAt: days(-1) }],
        ['PK-3101', WH.tal, 'Ferretería Huachipato', 'Salida hoy 13:00', 'PENDIENTE', null,
            [['PROD-105', 8, 0], ['PROD-110', 5, 0]]],
        ['PK-3102', WH.tal, 'Pesquera Talcahuano', 'Salida hoy 17:00', 'PREPARADO', 'opTal',
            [['PROD-102', 1, 1], ['PROD-118', 1, 1]]],
    ];
    for (const [code, wh, customer, due, status, assignee, items, extra = {}] of pickings) {
        const id = `${C}_${code}`;
        const a = assignee ? by(assignee) : { id: '', name: '' };
        const order = {
            companyId: C, warehouseId: wh.id, code, customerName: customer, dueLabel: due, status,
            lineCount: items.length, assignedToId: a.id, assignedToName: a.name, ...extra,
        };
        if (items.some(([, , picked]) => picked > 0)) order.pickedAt = days(status === 'DESPACHADO' ? -1 : 0, -1);
        put(`PICKING_ORDERS/${id}`, order);
        items.forEach(([pc, requested, picked], i) => {
            const item = { ...line(pc), requestedQuantity: requested, pickedQuantity: picked };
            if (status === 'DESPACHADO') item.verifiedQuantity = picked;
            put(`PICKING_ORDERS/${id}/ITEMS/L${i + 1}`, item);
        });
    }

    // ---------------- Conteos ----------------
    const inZone = z => Object.values(P).filter(p => p.location.startsWith(z));
    const inCategory = c => Object.values(P).filter(p => p.category === c);
    const counts = [
        ['CT-01', WH.central, 'ZONA', 'Conteo cíclico · Zona A', inZone('A')],
        ['CT-02', WH.central, 'CATEGORIA', 'Conteo cíclico · Categoría SEGURIDAD', inCategory('Seguridad')],
        ['CT-03', WH.tal, 'CATEGORIA', 'Conteo cíclico · Categoría HERRAMIENTAS', inCategory('Herramientas')],
    ];
    for (const [k, wh, type, name, products] of counts) {
        const id = `${C}_${k}`;
        put(`COUNT_TASKS/${id}`, {
            companyId: C, warehouseId: wh.id, name, type, status: 'PENDIENTE', lineCount: products.length,
            createdByName: wh === WH.tal ? USERS.supTal.fullName : USERS.sup.fullName, createdAt: days(0, -5),
        });
        products.forEach((p, i) => put(`COUNT_TASKS/${id}/ITEMS/L${i + 1}`, { ...line(p.code), countedQuantity: 0 }));
    }
    // Un conteo cerrado (zona D) con diferencias que quedaron como ajustes pendientes
    const closedName = 'Conteo cíclico · Zona D';
    const zoneD = inZone('D');
    const counted = { 'PROD-112': 21, 'PROD-114': 10 }; // el resto calza
    const diffs = [];
    zoneD.forEach((p, i) => {
        const system = p.stock[0];
        const c = counted[p.code] ?? system;
        if (c !== system) diffs.push([p, system, c]);
        put(`COUNT_TASKS/${C}_CT-00/ITEMS/L${i + 1}`, {
            ...line(p.code), countedQuantity: c, systemQuantity: system, difference: c - system,
        });
    });
    put(`COUNT_TASKS/${C}_CT-00`, {
        companyId: C, warehouseId: WH.central.id, name: closedName, type: 'ZONA', status: 'CERRADO',
        lineCount: zoneD.length, createdByName: USERS.sup.fullName, createdAt: days(-2),
        differencesCount: diffs.length,
        summary: diffs.map(([p, s, c]) => `${p.code} ${p.name}: sistema ${s}, contado ${c} (${c - s > 0 ? '+' : ''}${c - s})`).join('\n'),
        closedByName: USERS.op.fullName, closedAt: days(-1, -4),
    });

    // ---------------- Ajustes ----------------
    const adjustment = (k, wh, code, qty, reason, notes, createdBy, extra = {}) => {
        const p = P[code];
        const c = by(createdBy);
        put(`ADJUSTMENTS/${C}_${k}`, {
            companyId: C, warehouseId: wh.id, productId: p.id, productCode: code, productName: p.name,
            quantity: qty, reason, notes, reference: extra.reference ?? '', lotId: extra.lotId ?? '',
            lotNumber: extra.lotNumber ?? '', ...(extra.serials ? { serials: extra.serials } : {}),
            status: extra.status ?? 'PENDIENTE', createdById: c.id, createdByName: c.name,
            createdAt: extra.createdAt ?? days(0, -2),
            ...(extra.reviewedBy ? {
                reviewedById: by(extra.reviewedBy).id, reviewedByName: by(extra.reviewedBy).name,
                reviewedAt: extra.reviewedAt, reviewNote: extra.reviewNote ?? '',
            } : {}),
        });
    };
    adjustment('AJ-01', WH.central, 'PROD-105', -3, 'DANADO', 'Guantes rotos en caja abierta', 'op');
    adjustment('AJ-02', WH.central, 'PROD-111', -6, 'VENCIDO', 'Lote vencido detectado en pasillo D', 'op',
        { lotId: lotId(WH.central, 'PROD-111', 'L-SIL-2401'), lotNumber: 'L-SIL-2401' });
    adjustment('AJ-03', WH.central, 'PROD-116', -1, 'DANADO', 'Carcasa trizada al bajar de estantería', 'op',
        { serials: [snDamagedPending] });
    diffs.forEach(([p, s, c], i) => adjustment(`AJ-0${4 + i}`, WH.central, p.code, c - s, 'DIFERENCIA_CONTEO',
        `Sistema ${s} · contado ${c}`, 'op', { reference: closedName, createdAt: days(-1, -4) }));
    adjustment('AJ-10', WH.central, 'PROD-108', 2, 'INGRESO_NO_REGISTRADO', 'Encontrado en pasillo C', 'op',
        { status: 'APROBADO', reviewedBy: 'sup', reviewedAt: days(-3), reviewNote: 'Verificado en terreno', createdAt: days(-3, -2) });
    adjustment('AJ-11', WH.central, 'PROD-101', -5, 'MERMA', 'Cajas humedecidas', 'op',
        { status: 'RECHAZADO', reviewedBy: 'jefe', reviewedAt: days(-2), reviewNote: 'Las cajas están en A-01-01, pasillo 2', createdAt: days(-2, -3) });
    adjustment('AJ-12', WH.central, 'PROD-116', -1, 'DANADO', 'No enciende, motor quemado', 'op',
        { status: 'APROBADO', serials: [snRetired], reviewedBy: 'sup', reviewedAt: days(-2), reviewNote: 'Dar de baja', createdAt: days(-2, -1) });
    adjustment('AJ-20', WH.tal, 'PROD-119', -1, 'EXTRAVIADO', 'No aparece tras inventario de pasillo', 'opTal');

    // ---------------- Transferencias ----------------
    const transfer = (code, from, to, status, notes, createdBy, items, extra = {}) => {
        const id = `${C}_${code}`;
        put(`TRANSFERS/${id}`, {
            companyId: C, code, originWarehouseId: from.id, originWarehouseName: from.name,
            destinationWarehouseId: to.id, destinationWarehouseName: to.name, status,
            lineCount: items.length, notes, createdByName: USERS[createdBy].fullName,
            createdAt: extra.createdAt ?? days(0, -6), ...extra.header,
        });
        items.forEach(([pc, qty, more = {}], i) => put(`TRANSFERS/${id}/ITEMS/L${i + 1}`, {
            ...line(pc), quantity: qty,
            sentQuantity: status === 'POR_ENVIAR' ? 0 : qty,
            receivedQuantity: status === 'RECIBIDA' ? qty : 0, ...more,
        }));
    };
    transfer('TR-0101', WH.central, WH.tal, 'POR_ENVIAR', 'Reposición semanal', 'sup',
        [['PROD-105', 20], ['PROD-106', 4]]);
    transfer('TR-0102', WH.central, WH.chi, 'POR_ENVIAR', 'Sucursal sin stock de sierras', 'jefe',
        [['PROD-102', 3], ['PROD-103', 5], ['PROD-117', 1]]);
    transfer('TR-0103', WH.tal, WH.central, 'EN_TRANSITO', 'Devolución de sobrestock', 'supTal',
        [['PROD-111', 4, { lots: 'L-SIL-2403×4' }], ['PROD-107', 1, { serials: [snInTransit] }]],
        { header: { carrier: 'Transportes Biobío', sentAt: days(0, -2), sentByName: USERS.opTal.fullName } });
    transfer('TR-0104', WH.tal, WH.central, 'POR_ENVIAR', 'Tarugos para obra Penco', 'supTal',
        [['PROD-110', 5]]);
    transfer('TR-0099', WH.chi, WH.central, 'RECIBIDA', 'Consolidación de lentes', 'jefe',
        [['PROD-115', 10]],
        { createdAt: days(-4), header: { carrier: 'Starken', sentAt: days(-3), receivedAt: days(-2), hasDiscrepancy: false } });

    // ---------------- Bitácora de movimientos (últimos días) ----------------
    // [k, tipo, bodega, producto, cantidad, referencia, usuario, cuándo, lote, series]
    const movements = [
        ['M01', 'RECEPCION', WH.central, 'PROD-102', 10, 'OC-4498', 'op', days(-1, -6)],
        ['M02', 'RECEPCION', WH.central, 'PROD-104', 12, 'OC-4498', 'op', days(-1, -6)],
        ['M03', 'RECEPCION', WH.central, 'PROD-111', 12, 'OC-4502', 'op', days(0, -3), 'L-SIL-2403'],
        ['M04', 'RECEPCION', WH.central, 'PROD-113', 10, 'OC-4502', 'op', days(0, -3), 'L-PIN-1103'],
        ['M05', 'DESPACHO', WH.central, 'PROD-101', -10, 'PK-3007 · GD-000451', 'op', days(-1)],
        ['M06', 'DESPACHO', WH.central, 'PROD-103', -2, 'PK-3007 · GD-000451', 'op', days(-1)],
        ['M07', 'DESPACHO', WH.central, 'PROD-107', -1, 'PK-2990', 'op', days(-4), null, snDispatched],
        ['M08', 'TRANSFERENCIA_SALIDA', WH.chi, 'PROD-115', -10, `TR-0099 → ${WH.central.name}`, 'jefe', days(-3)],
        ['M09', 'TRANSFERENCIA_ENTRADA', WH.central, 'PROD-115', 10, `TR-0099 ← ${WH.chi.name}`, 'op', days(-2)],
        ['M10', 'TRANSFERENCIA_SALIDA', WH.tal, 'PROD-111', -4, `TR-0103 → ${WH.central.name}`, 'opTal', days(0, -2), 'L-SIL-2403×4'],
        ['M11', 'TRANSFERENCIA_SALIDA', WH.tal, 'PROD-107', -1, `TR-0103 → ${WH.central.name}`, 'opTal', days(0, -2), null, snInTransit],
        ['M12', 'AJUSTE', WH.central, 'PROD-108', 2, 'Ingreso no registrado', 'sup', days(-3)],
        ['M13', 'AJUSTE', WH.central, 'PROD-116', -1, 'Producto dañado', 'sup', days(-2), null, snRetired],
        ['M14', 'REUBICACION', WH.central, 'PROD-118', 0, 'C-02-05 → C-03-01', 'op', days(-5)],
    ];
    for (const [k, type, wh, pc, qty, reference, user, when, lot, serials] of movements) {
        const p = P[pc];
        put(`MOVEMENTS/${C}_${k}`, {
            companyId: C, warehouseId: wh.id, productId: p.id, productCode: pc, productName: p.name,
            type, quantity: qty, reference, ...(lot ? { lot } : {}), ...(serials ? { serials } : {}),
            userId: uid[user], userName: USERS[user].fullName, createdAt: when,
        });
    }

    await writer.close();

    console.log(`Listo: ${writes} documentos escritos.\n`);
    console.log(`En la app: código de empresa "${C}", contraseña "${PASSWORD}" para todos:`);
    for (const u of Object.values(USERS)) {
        console.log(`  ${u.role.padEnd(12)} ${u.email.padEnd(36)} ${u.fullName} · ${u.wh.name}`);
    }
}

main().catch(e => {
    console.error(e);
    process.exit(1);
});
