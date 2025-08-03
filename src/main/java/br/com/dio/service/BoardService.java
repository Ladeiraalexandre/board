package br.com.dio.service;

import br.com.dio.persistence.dao.BoardColumnDAO;
import br.com.dio.persistence.dao.BoardDAO;
import br.com.dio.persistence.entity.BoardEntity;
import lombok.AllArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@AllArgsConstructor
public class BoardService {

    private final Connection connection;

    public BoardEntity insert(final BoardEntity entity) throws SQLException {
        var dao = new BoardDAO(connection);
        var boardColumnDAO = new BoardColumnDAO(connection);

        try {
            dao.insert(entity);
            insertBoardColumns(entity, boardColumnDAO);
            connection.commit();
            return entity;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }

    private void insertBoardColumns(BoardEntity entity, BoardColumnDAO boardColumnDAO) throws SQLException {
        List<?> columns = entity.getBoardColumns();
        for (var column : columns) {
            column.setBoard(entity);
            boardColumnDAO.insert(column);
        }
    }

    public boolean delete(final Long id) throws SQLException {
        var dao = new BoardDAO(connection);

        try {
            if (!dao.exists(id)) {
                return false;
            }
            dao.delete(id);
            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }
}
