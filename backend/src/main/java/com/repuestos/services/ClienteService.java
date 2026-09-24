package com.repuestos.services;

import com.repuestos.models.Cliente;
import com.repuestos.repositories.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteService {
    
    private final ClienteRepository clienteRepository;
    
    public List<Cliente> listarTodos() {
        return clienteRepository.findByActivoTrue();
    }
    
    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }
    
    public List<Cliente> buscar(String termino) {
        return clienteRepository.buscar(termino);
    }
    
    public List<Cliente> clientesConDeuda() {
        return clienteRepository.findClientesConDeuda();
    }
    
    public Cliente guardar(Cliente cliente) {
        return clienteRepository.save(cliente);
    }
    
    public Cliente actualizar(Long id, Cliente clienteActualizado) {
        Cliente cliente = buscarPorId(id);
        cliente.setNombre(clienteActualizado.getNombre());
        cliente.setTelefono(clienteActualizado.getTelefono());
        cliente.setEmail(clienteActualizado.getEmail());
        cliente.setDireccion(clienteActualizado.getDireccion());
        cliente.setCuit(clienteActualizado.getCuit());
        cliente.setCondicionIva(clienteActualizado.getCondicionIva());
        cliente.setObservaciones(clienteActualizado.getObservaciones());
        return clienteRepository.save(cliente);
    }
    
    public void eliminar(Long id) {
        Cliente cliente = buscarPorId(id);
        cliente.setActivo(false);
        clienteRepository.save(cliente);
    }
    
    public Cliente ajustarSaldo(Long id, BigDecimal monto, String tipo) {
        Cliente cliente = buscarPorId(id);
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }

        BigDecimal saldoActual = cliente.getSaldoCorriente() == null
                ? BigDecimal.ZERO
                : cliente.getSaldoCorriente();

        if ("sumar".equals(tipo)) {
            cliente.setSaldoCorriente(saldoActual.add(monto));
        } else if ("restar".equals(tipo)) {
            if (monto.compareTo(saldoActual) > 0) {
                throw new IllegalArgumentException("El pago no puede superar la deuda pendiente");
            }
            cliente.setSaldoCorriente(saldoActual.subtract(monto));
        } else {
            throw new IllegalArgumentException("Tipo de ajuste inválido");
        }
        return clienteRepository.save(cliente);
    }
}
