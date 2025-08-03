package br.com.dio.ui;

import br.com.dio.dto.BoardColumnInfoDTO;
import br.com.dio.persistence.entity.BoardColumnEntity;
import br.com.dio.persistence.entity.BoardEntity;
import br.com.dio.persistence.entity.CardEntity;
import br.com.dio.service.BoardColumnQueryService;
import br.com.dio.service.BoardQueryService;
import br.com.dio.service.CardQueryService;
import br.com.dio.service.CardService;
import lombok.AllArgsConstructor;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;

import static br.com.dio.persistence.config.ConnectionConfig.getConnection;

@AllArgsConstructor
public class BoardMenu {

    private final BoardEntity entity;
    private final Scanner scanner = new Scanner(System.in).useDelimiter("\n");

    public void execute() {
        System.out.printf("Bem vindo ao board %s, selecione a operação desejada\n", entity.getId());
        int option;
        do {
            printMenu();
            option = readInt();
            handleOption(option);
        } while (option != 9 && option != 10);
    }

    private void printMenu() {
        System.out.println("""
                1 - Criar um card
                2 - Mover um card
                3 - Bloquear um card
                4 - Desbloquear um card
                5 - Cancelar um card
                6 - Ver board
                7 - Ver coluna com cards
                8 - Ver card
                9 - Voltar para o menu anterior
                10 - Sair
                """);
    }

    private void handleOption(int option) {
        try {
            switch (option) {
                case 1 -> createCard();
                case 2 -> moveCardToNextColumn();
                case 3 -> blockCard();
                case 4 -> unblockCard();
                case 5 -> cancelCard();
                case 6 -> showBoard();
                case 7 -> showColumn();
                case 8 -> showCard();
                case 9 -> System.out.println("Voltando para o menu anterior");
                case 10 -> System.exit(0);
                default -> System.out.println("Opção inválida, informe uma opção do menu");
            }
        } catch (SQLException ex) {
            System.out.println("Erro ao executar operação: " + ex.getMessage());
        }
    }

    private int readInt() {
        while (!scanner.hasNextInt()) {
            System.out.println("Informe um número válido:");
            scanner.next();
        }
        return scanner.nextInt();
    }

    private long readLong(String prompt) {
        System.out.println(prompt);
        while (!scanner.hasNextLong()) {
            System.out.println("Informe um número válido:");
            scanner.next();
        }
        return scanner.nextLong();
    }

    private String readString(String prompt) {
        System.out.println(prompt);
        return scanner.next();
    }

    private void createCard() throws SQLException {
        var card = new CardEntity();
        card.setTitle(readString("Informe o título do card"));
        card.setDescription(readString("Informe a descrição do card"));
        card.setBoardColumn(entity.getInitialColumn());
        executeWithConnection(conn -> new CardService(conn).create(card));
        System.out.println("Card criado com sucesso!");
    }

    private void moveCardToNextColumn() throws SQLException {
        var cardId = readLong("Informe o id do card que deseja mover para a próxima coluna");
        var boardColumnsInfo = getBoardColumnsInfo();
        executeWithConnection(conn -> {
            try {
                new CardService(conn).moveToNextColumn(cardId, boardColumnsInfo);
                System.out.println("Card movido com sucesso!");
            } catch (RuntimeException ex) {
                System.out.println(ex.getMessage());
            }
        });
    }

    private void blockCard() throws SQLException {
        var cardId = readLong("Informe o id do card que será bloqueado");
        var reason = readString("Informe o motivo do bloqueio do card");
        var boardColumnsInfo = getBoardColumnsInfo();
        executeWithConnection(conn -> {
            try {
                new CardService(conn).block(cardId, reason, boardColumnsInfo);
                System.out.println("Card bloqueado com sucesso!");
            } catch (RuntimeException ex) {
                System.out.println(ex.getMessage());
            }
        });
    }

    private void unblockCard() throws SQLException {
        var cardId = readLong("Informe o id do card que será desbloqueado");
        var reason = readString("Informe o motivo do desbloqueio do card");
        executeWithConnection(conn -> {
            try {
                new CardService(conn).unblock(cardId, reason);
                System.out.println("Card desbloqueado com sucesso!");
            } catch (RuntimeException ex) {
                System.out.println(ex.getMessage());
            }
        });
    }

    private void cancelCard() throws SQLException {
        var cardId = readLong("Informe o id do card que deseja mover para a coluna de cancelamento");
        var cancelColumn = entity.getCancelColumn();
        var boardColumnsInfo = getBoardColumnsInfo();
        executeWithConnection(conn -> {
            try {
                new CardService(conn).cancel(cardId, cancelColumn.getId(), boardColumnsInfo);
                System.out.println("Card cancelado com sucesso!");
            } catch (RuntimeException ex) {
                System.out.println(ex.getMessage());
            }
        });
    }

    private void showBoard() throws SQLException {
        executeWithConnection(conn -> {
            var optional = new BoardQueryService(conn).showBoardDetails(entity.getId());
            optional.ifPresentOrElse(
                b -> {
                    System.out.printf("Board [%s,%s]\n", b.id(), b.name());
                    b.columns().forEach(c ->
                        System.out.printf("Coluna [%s] tipo: [%s] tem %s cards\n", c.name(), c.kind(), c.cardsAmount())
                    );
                },
                () -> System.out.println("Board não encontrado.")
            );
        });
    }

    private void showColumn() throws SQLException {
        var columnsIds = entity.getBoardColumns().stream().map(BoardColumnEntity::getId).toList();
        long selectedColumnId;
        do {
            System.out.printf("Escolha uma coluna do board %s pelo id\n", entity.getName());
            entity.getBoardColumns().forEach(c -> System.out.printf("%s - %s [%s]\n", c.getId(), c.getName(), c.getKind()));
            selectedColumnId = readLong("");
        } while (!columnsIds.contains(selectedColumnId));
        executeWithConnection(conn -> {
            var column = new BoardColumnQueryService(conn).findById(selectedColumnId);
            column.ifPresentOrElse(
                co -> {
                    System.out.printf("Coluna %s tipo %s\n", co.getName(), co.getKind());
                    co.getCards().forEach(ca -> System.out.printf("Card %s - %s\nDescrição: %s\n",
                            ca.getId(), ca.getTitle(), ca.getDescription()));
                },
                () -> System.out.println("Coluna não encontrada.")
            );
        });
    }

    private void showCard() throws SQLException {
        var selectedCardId = readLong("Informe o id do card que deseja visualizar");
        executeWithConnection(conn -> {
            new CardQueryService(conn).findById(selectedCardId)
                .ifPresentOrElse(
                    c -> {
                        System.out.printf("Card %s - %s.\n", c.id(), c.title());
                        System.out.printf("Descrição: %s\n", c.description());
                        System.out.println(c.blocked() ?
                                "Está bloqueado. Motivo: " + c.blockReason() :
                                "Não está bloqueado");
                        System.out.printf("Já foi bloqueado %s vezes\n", c.blocksAmount());
                        System.out.printf("Está no momento na coluna %s - %s\n", c.columnId(), c.columnName());
                    },
                    () -> System.out.printf("Não existe um card com o id %s\n", selectedCardId)
                );
        });
    }

    private void executeWithConnection(Consumer<java.sql.Connection> action) {
        try (var connection = getConnection()) {
            action.accept(connection);
        } catch (SQLException ex) {
            System.out.println("Erro de conexão: " + ex.getMessage());
        }
    }

    private List<BoardColumnInfoDTO> getBoardColumnsInfo() {
        return entity.getBoardColumns().stream()
                .map(bc -> new BoardColumnInfoDTO(bc.getId(), bc.getOrder(), bc.getKind()))
                .toList();
    }
}
