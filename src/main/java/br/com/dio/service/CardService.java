package br.com.dio.service;

import br.com.dio.dto.BoardColumnInfoDTO;
import br.com.dio.exception.CardBlockedException;
import br.com.dio.exception.CardFinishedException;
import br.com.dio.exception.EntityNotFoundException;
import br.com.dio.persistence.dao.BlockDAO;
import br.com.dio.persistence.dao.CardDAO;
import br.com.dio.persistence.entity.CardEntity;
import lombok.AllArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static br.com.dio.persistence.entity.BoardColumnKindEnum.CANCEL;
import static br.com.dio.persistence.entity.BoardColumnKindEnum.FINAL;

@AllArgsConstructor
public class CardService {

    private final Connection connection;

    public CardEntity create(final CardEntity entity) throws SQLException {
        return executeTransaction(() -> {
            var dao = new CardDAO(connection);
            dao.insert(entity);
            return entity;
        });
    }

    public void moveToNextColumn(final Long cardId, final List<BoardColumnInfoDTO> boardColumnsInfo) throws SQLException {
        executeTransaction(() -> {
            var dao = new CardDAO(connection);
            var dto = getCardOrThrow(dao, cardId);
            validateNotBlocked(dto, cardId);
            var currentColumn = getCurrentColumn(boardColumnsInfo, dto.columnId());
            if (currentColumn.kind().equals(FINAL)) {
                throw new CardFinishedException("O card já foi finalizado");
            }
            var nextColumn = boardColumnsInfo.stream()
                    .filter(bc -> bc.order() == currentColumn.order() + 1)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("O card está cancelado"));
            dao.moveToColumn(nextColumn.id(), cardId);
            return null;
        });
    }

    public void cancel(final Long cardId, final Long cancelColumnId, final List<BoardColumnInfoDTO> boardColumnsInfo) throws SQLException {
        executeTransaction(() -> {
            var dao = new CardDAO(connection);
            var dto = getCardOrThrow(dao, cardId);
            validateNotBlocked(dto, cardId);
            var currentColumn = getCurrentColumn(boardColumnsInfo, dto.columnId());
            if (currentColumn.kind().equals(FINAL)) {
                throw new CardFinishedException("O card já foi finalizado");
            }
            boardColumnsInfo.stream()
                    .filter(bc -> bc.order() == currentColumn.order() + 1)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("O card está cancelado"));
            dao.moveToColumn(cancelColumnId, cardId);
            return null;
        });
    }

    public void block(final Long id, final String reason, final List<BoardColumnInfoDTO> boardColumnsInfo) throws SQLException {
        executeTransaction(() -> {
            var dao = new CardDAO(connection);
            var dto = getCardOrThrow(dao, id);
            if (dto.blocked()) {
                throw new CardBlockedException("O card %s já está bloqueado".formatted(id));
            }
            var currentColumn = getCurrentColumn(boardColumnsInfo, dto.columnId());
            if (currentColumn.kind().equals(FINAL) || currentColumn.kind().equals(CANCEL)) {
                throw new IllegalStateException("O card está em uma coluna do tipo %s e não pode ser bloqueado"
                        .formatted(currentColumn.kind()));
            }
            var blockDAO = new BlockDAO(connection);
            blockDAO.block(reason, id);
            return null;
        });
    }

    public void unblock(final Long id, final String reason) throws SQLException {
        executeTransaction(() -> {
            var dao = new CardDAO(connection);
            var dto = getCardOrThrow(dao, id);
            if (!dto.blocked()) {
                throw new CardBlockedException("O card %s não está bloqueado".formatted(id));
            }
            var blockDAO = new BlockDAO(connection);
            blockDAO.unblock(reason, id);
            return null;
        });
    }

    // Métodos auxiliares para evitar duplicidade

    private <T> T executeTransaction(TransactionCallback<T> callback) throws SQLException {
        try {
            T result = callback.doInTransaction();
            connection.commit();
            return result;
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        }
    }

    private BoardColumnInfoDTO getCurrentColumn(List<BoardColumnInfoDTO> columns, Long columnId) {
        return columns.stream()
                .filter(bc -> bc.id().equals(columnId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("O card informado pertence a outro board"));
    }

    private void validateNotBlocked(CardEntity dto, Long cardId) {
        if (dto.blocked()) {
            throw new CardBlockedException("O card %s está bloqueado, é necessário desbloqueá-lo para mover".formatted(cardId));
        }
    }

    private CardEntity getCardOrThrow(CardDAO dao, Long cardId) {
        return dao.findById(cardId)
                .orElseThrow(() -> new EntityNotFoundException("O card de id %s não foi encontrado".formatted(cardId)));
    }

    @FunctionalInterface
    private interface TransactionCallback<T> {
        T doInTransaction() throws SQLException;
    }
}
