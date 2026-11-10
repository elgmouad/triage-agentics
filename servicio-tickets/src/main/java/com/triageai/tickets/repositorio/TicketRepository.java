package com.triageai.tickets.repositorio;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.triageai.tickets.dominio.EstadoTicket;
import com.triageai.tickets.dominio.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findTop5ByClienteIdOrderByCreadoEnDesc(UUID clienteId);

    long countByClienteId(UUID clienteId);

    List<Ticket> findByEscaladoFalseAndEstadoIn(Collection<EstadoTicket> estados);

    List<Ticket> findByEstadoAndResueltoEnBefore(EstadoTicket estado, Instant limite);
}
