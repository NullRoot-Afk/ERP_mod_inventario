// Pruebas de firestore.rules. Ejecutar con:  npm install && npm test
// (levanta el emulador de Firestore; requiere Java 11+).
import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Bytes,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  increment,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

const RULES = readFileSync(fileURLToPath(new URL('../firestore.rules', import.meta.url)), 'utf8');

let env;

const USERS = {
  op1: { companyId: 'ACME', role: 'OPERARIO', warehouseId: 'W1', email: 'op1@acme.cl' },
  sup1: { companyId: 'ACME', role: 'supervisor', warehouseId: 'W1', email: 'sup1@acme.cl' }, // rol en minúsculas a propósito
  op2: { companyId: 'ACME', role: 'OPERARIO', warehouseId: 'W2', email: 'op2@acme.cl' },
  intruso: { companyId: 'OTRA', role: 'JEFE_BODEGA', warehouseId: 'X1', email: 'x@otra.cl' },
};

function db(uid) {
  return env.authenticatedContext(uid, { email: USERS[uid].email }).firestore();
}

/** Mismas escrituras que la app al aprobar un ajuste (AdjustmentRepositoryImpl.approve). */
function approveBatch(fs, uid) {
  const b = writeBatch(fs);
  b.update(doc(fs, 'ADJUSTMENTS/a1'), {
    status: 'APROBADO', reviewedById: uid, reviewedByName: uid, reviewedAt: serverTimestamp(), reviewNote: '',
  });
  b.set(doc(fs, 'PRODUCTS/p1'), { companyId: 'ACME', stock: { W1: increment(-3) } }, { merge: true });
  b.set(doc(collection(fs, 'MOVEMENTS')), {
    companyId: 'ACME', warehouseId: 'W1', productId: 'p1', type: 'AJUSTE', quantity: -3, userId: uid,
  });
  return b.commit();
}

/** Mismas escrituras que DispatchRepositoryImpl.save. */
function dispatchBatch(fs, uid) {
  const b = writeBatch(fs);
  b.update(doc(fs, 'PICKING_ORDERS/o1/ITEMS/i1'), { verifiedQuantity: 5 });
  b.set(doc(fs, 'PRODUCTS/p1'), { companyId: 'ACME', stock: { W1: increment(-5) } }, { merge: true });
  b.set(doc(collection(fs, 'MOVEMENTS')), {
    companyId: 'ACME', warehouseId: 'W1', productId: 'p1', type: 'DESPACHO', quantity: -5, userId: uid,
  });
  b.update(doc(fs, 'PICKING_ORDERS/o1'), { status: 'DESPACHADO', guideNumber: 'GD-1', dispatchedById: uid });
  return b.commit();
}

async function stockOfP1() {
  let value;
  await env.withSecurityRulesDisabled(async (ctx) => {
    value = (await getDoc(doc(ctx.firestore(), 'PRODUCTS/p1'))).data().stock.W1;
  });
  return value;
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-ruka',
    firestore: { rules: RULES, host: '127.0.0.1', port: 8080 },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    for (const [uid, data] of Object.entries(USERS)) await setDoc(doc(fs, `USERS/${uid}`), data);
    await setDoc(doc(fs, 'COMPANIES/ACME'), { name: 'Acme', code: 'ACME' });
    await setDoc(doc(fs, 'PRODUCTS/p1'), { companyId: 'ACME', code: 'PROD-1', stock: { W1: 50 } });
    await setDoc(doc(fs, 'ADJUSTMENTS/a1'), { companyId: 'ACME', warehouseId: 'W1', status: 'PENDIENTE', quantity: -3, createdById: 'op1' });
    await setDoc(doc(fs, 'PICKING_ORDERS/o1'), { companyId: 'ACME', warehouseId: 'W1', status: 'PREPARADO' });
    await setDoc(doc(fs, 'PICKING_ORDERS/o1/ITEMS/i1'), { productId: 'p1', pickedQuantity: 5 });
    await setDoc(doc(fs, 'COUNT_TASKS/c1'), { companyId: 'ACME', warehouseId: 'W1', status: 'PENDIENTE' });
    await setDoc(doc(fs, 'TRANSFERS/t1'), {
      companyId: 'ACME', status: 'POR_ENVIAR', originWarehouseId: 'W1', destinationWarehouseId: 'W2',
    });
    await setDoc(doc(fs, 'TRANSFERS/t1/ITEMS/i1'), { productId: 'p1', quantity: 4 });
    await setDoc(doc(fs, 'LOTS/l1'), { companyId: 'ACME', warehouseId: 'W1', status: 'ACTIVO', quantity: 10 });
    await setDoc(doc(fs, 'DISCREPANCIES/d1'), { companyId: 'ACME', warehouseId: 'W1', status: 'ABIERTA' });
    await setDoc(doc(fs, 'RECEPTIONS/r1'), { companyId: 'ACME', status: 'SIN_INICIAR' });
  });
});

describe('Empresas y usuarios', () => {
  test('el código de empresa se valida sin sesión', async () => {
    await assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), 'COMPANIES/ACME')));
  });

  test('cada uno lee solo su documento de usuario (o el suyo por email)', async () => {
    await assertSucceeds(getDoc(doc(db('op1'), 'USERS/op1')));
    await assertFails(getDoc(doc(db('op1'), 'USERS/sup1')));
    await assertSucceeds(getDocs(query(collection(db('op1'), 'USERS'), where('email', '==', 'op1@acme.cl'))));
    await assertFails(getDocs(query(collection(db('op1'), 'USERS'), where('email', '==', 'sup1@acme.cl'))));
  });

  test('sin sesión no se leen datos', async () => {
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'PRODUCTS/p1')));
  });
});

describe('Aislamiento por empresa', () => {
  test('un usuario de otra empresa no lee ni escribe', async () => {
    await assertSucceeds(getDoc(doc(db('op1'), 'PRODUCTS/p1')));
    await assertFails(getDoc(doc(db('intruso'), 'PRODUCTS/p1')));
    await assertFails(setDoc(doc(db('intruso'), 'PRODUCTS/p1'), { companyId: 'OTRA' }, { merge: true }));
  });

  test('las consultas deben filtrar por la propia empresa', async () => {
    await assertSucceeds(getDocs(query(collection(db('op1'), 'PRODUCTS'), where('companyId', '==', 'ACME'))));
    await assertFails(getDocs(query(collection(db('intruso'), 'PRODUCTS'), where('companyId', '==', 'ACME'))));
    await assertFails(getDocs(collection(db('op1'), 'PRODUCTS'))); // sin filtro
  });

  test('las líneas (ITEMS) heredan la empresa del documento padre', async () => {
    await assertSucceeds(getDocs(collection(db('op1'), 'PICKING_ORDERS/o1/ITEMS')));
    await assertFails(getDocs(collection(db('intruso'), 'PICKING_ORDERS/o1/ITEMS')));
  });

  test('cabecera + líneas en el mismo batch (crear transferencia)', async () => {
    const fs = db('op1');
    const b = writeBatch(fs);
    b.set(doc(fs, 'TRANSFERS/t2'), { companyId: 'ACME', status: 'POR_ENVIAR', originWarehouseId: 'W1', destinationWarehouseId: 'W2' });
    b.set(doc(fs, 'TRANSFERS/t2/ITEMS/x'), { productId: 'p1', quantity: 1 });
    await assertSucceeds(b.commit());
  });
});

describe('Bitácora de movimientos', () => {
  test('se firma con el uid real y no se edita', async () => {
    const fs = db('op1');
    await assertSucceeds(setDoc(doc(fs, 'MOVEMENTS/m1'), { companyId: 'ACME', userId: 'op1', quantity: 1 }));
    await assertFails(setDoc(doc(fs, 'MOVEMENTS/m2'), { companyId: 'ACME', userId: 'sup1', quantity: 1 }));
    await assertFails(updateDoc(doc(fs, 'MOVEMENTS/m1'), { quantity: 99 }));
    await assertFails(deleteDoc(doc(fs, 'MOVEMENTS/m1')));
  });
});

describe('Ajustes: el operario reporta, el supervisor aprueba una sola vez', () => {
  test('el operario crea pendientes pero no aprueba', async () => {
    await assertSucceeds(setDoc(doc(db('op1'), 'ADJUSTMENTS/a2'), { companyId: 'ACME', status: 'PENDIENTE', createdById: 'op1' }));
    await assertFails(setDoc(doc(db('op1'), 'ADJUSTMENTS/a3'), { companyId: 'ACME', status: 'APROBADO', createdById: 'op1' }));
    await assertFails(approveBatch(db('op1'), 'op1'));
  });

  test('segunda aprobación rechazada y el stock no se descuenta dos veces', async () => {
    await assertSucceeds(approveBatch(db('sup1'), 'sup1'));
    assert.equal(await stockOfP1(), 47);
    await assertFails(approveBatch(db('sup1'), 'sup1'));
    assert.equal(await stockOfP1(), 47);
  });

  test('no se puede rechazar un ajuste ya aprobado', async () => {
    await assertSucceeds(approveBatch(db('sup1'), 'sup1'));
    await assertFails(updateDoc(doc(db('sup1'), 'ADJUSTMENTS/a1'), {
      status: 'RECHAZADO', reviewedById: 'sup1', reviewedByName: 'sup1', reviewNote: 'x',
    }));
  });

  test('al revisar no se puede cambiar la cantidad', async () => {
    await assertFails(updateDoc(doc(db('sup1'), 'ADJUSTMENTS/a1'), {
      status: 'APROBADO', reviewedById: 'sup1', quantity: -300,
    }));
  });
});

describe('Despacho: un pedido despachado es final', () => {
  test('el segundo despacho (otro dispositivo u offline) se rechaza completo', async () => {
    await assertSucceeds(dispatchBatch(db('op1'), 'op1'));
    assert.equal(await stockOfP1(), 45);
    await assertFails(dispatchBatch(db('op2'), 'op2'));
    assert.equal(await stockOfP1(), 45);
  });

  test('no se puede retroceder el estado ni tocar sus líneas', async () => {
    await assertSucceeds(dispatchBatch(db('op1'), 'op1'));
    await assertFails(updateDoc(doc(db('op1'), 'PICKING_ORDERS/o1'), { status: 'EN_PROCESO' }));
    await assertFails(updateDoc(doc(db('op1'), 'PICKING_ORDERS/o1/ITEMS/i1'), { pickedQuantity: 0 }));
  });

  test('tomar un pedido pendiente', async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), 'PICKING_ORDERS/o2'), { companyId: 'ACME', status: 'PENDIENTE', assignedToId: '' }));
    await assertSucceeds(updateDoc(doc(db('op1'), 'PICKING_ORDERS/o2'), { status: 'EN_PROCESO', assignedToId: 'op1' }));
    await assertFails(updateDoc(doc(db('op1'), 'PICKING_ORDERS/o2'), { status: 'DESPACHADO' }));
  });
});

describe('Conteo: cerrar una sola vez', () => {
  test('el segundo cierre se rechaza', async () => {
    const close = (fs) => {
      const b = writeBatch(fs);
      b.update(doc(fs, 'COUNT_TASKS/c1'), { status: 'CERRADO', differencesCount: 1 });
      b.set(doc(collection(fs, 'ADJUSTMENTS')), { companyId: 'ACME', status: 'PENDIENTE', createdById: 'op1', quantity: 2 });
      return b.commit();
    };
    await assertSucceeds(close(db('op1')));
    await assertFails(close(db('op1')));
  });
});

describe('Transferencias: origen envía, destino recibe', () => {
  const send = (fs, uid) => {
    const b = writeBatch(fs);
    b.update(doc(fs, 'TRANSFERS/t1/ITEMS/i1'), { sentQuantity: 4 });
    b.set(doc(fs, 'PRODUCTS/p1'), { companyId: 'ACME', stock: { W1: increment(-4) } }, { merge: true });
    b.update(doc(fs, 'TRANSFERS/t1'), { status: 'EN_TRANSITO', carrier: 'X' });
    return b.commit();
  };
  const receive = (fs) => {
    const b = writeBatch(fs);
    b.update(doc(fs, 'TRANSFERS/t1/ITEMS/i1'), { receivedQuantity: 4 });
    b.set(doc(fs, 'PRODUCTS/p1'), { companyId: 'ACME', stock: { W2: increment(4) } }, { merge: true });
    b.update(doc(fs, 'TRANSFERS/t1'), { status: 'RECIBIDA' });
    return b.commit();
  };

  test('flujo completo y sin repeticiones', async () => {
    await assertFails(send(db('op2'), 'op2'));      // W2 no es el origen
    await assertSucceeds(send(db('op1'), 'op1'));
    await assertFails(send(db('op1'), 'op1'));      // ya enviada
    await assertFails(receive(db('op1')));          // W1 no es el destino
    await assertSucceeds(receive(db('op2')));
    await assertFails(receive(db('op2')));          // ya recibida
  });
});

describe('Lotes', () => {
  const retire = (fs) => {
    const b = writeBatch(fs);
    b.update(doc(fs, 'LOTS/l1'), { status: 'RETIRADO', retiredByName: 'op1', retiredAt: serverTimestamp() });
    b.set(doc(collection(fs, 'ADJUSTMENTS')), { companyId: 'ACME', status: 'PENDIENTE', createdById: 'op1', quantity: -10 });
    return b.commit();
  };

  test('un lote no se retira dos veces', async () => {
    await assertSucceeds(retire(db('op1')));
    await assertFails(retire(db('op1')));
  });

  test('consumo FEFO y nuevo ingreso del mismo lote', async () => {
    await assertSucceeds(updateDoc(doc(db('op1'), 'LOTS/l1'), { quantity: increment(-4) }));
    await assertSucceeds(setDoc(doc(db('op1'), 'LOTS/l1'), { companyId: 'ACME', quantity: increment(6), status: 'ACTIVO' }, { merge: true }));
  });
});

describe('Discrepancias y evidencia', () => {
  test('solo el supervisor resuelve, una vez', async () => {
    const resolve = (fs) => updateDoc(doc(fs, 'DISCREPANCIES/d1'), {
      status: 'RESUELTA', resolvedByName: 'x', resolvedAt: serverTimestamp(),
    });
    await assertFails(resolve(db('op1')));
    await assertSucceeds(resolve(db('sup1')));
    await assertFails(resolve(db('sup1')));
  });

  test('las fotos deben pesar menos de 1 MB', async () => {
    const small = Bytes.fromUint8Array(new Uint8Array(1000));
    const big = Bytes.fromUint8Array(new Uint8Array(1_000_001));
    await assertSucceeds(setDoc(doc(db('op1'), 'EVIDENCE/e1'), { companyId: 'ACME', image: small }));
    await assertFails(setDoc(doc(db('op1'), 'EVIDENCE/e2'), { companyId: 'ACME', image: big }));
  });
});

describe('Recepción', () => {
  test('el operario no elimina órdenes; el supervisor sí', async () => {
    await assertFails(deleteDoc(doc(db('op1'), 'RECEPTIONS/r1')));
    await assertSucceeds(deleteDoc(doc(db('sup1'), 'RECEPTIONS/r1')));
  });

  test('registrar lo recibido con incrementos', async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), 'RECEPTIONS/r1/ITEMS/i1'), { receivedQuantity: 0, orderQuantity: 10 }));
    const fs = db('op1');
    const b = writeBatch(fs);
    b.update(doc(fs, 'RECEPTIONS/r1/ITEMS/i1'), { receivedQuantity: increment(5) });
    b.update(doc(fs, 'RECEPTIONS/r1'), { status: 'PARCIAL' });
    await assertSucceeds(b.commit());
  });
});
