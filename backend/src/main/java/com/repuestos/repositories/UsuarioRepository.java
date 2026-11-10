package com.repuestos.repositories;

import com.repuestos.models.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByUsernameIgnoreCase(String username);
    Boolean existsByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    long countByRolAndActivoTrue(Usuario.Rol rol);
    List<Usuario> findAllByOrderByIdAsc();
    List<Usuario> findByRolAndClienteIsNull(Usuario.Rol rol);
    Optional<Usuario> findByClienteId(Long clienteId);
    boolean existsByClienteId(Long clienteId);
    long countByFotoUrl(String fotoUrl);

    @org.springframework.data.jpa.repository.Query("SELECT u.cliente.id FROM Usuario u WHERE u.cliente IS NOT NULL")
    java.util.List<Long> findClienteIdsConCuenta();
}
