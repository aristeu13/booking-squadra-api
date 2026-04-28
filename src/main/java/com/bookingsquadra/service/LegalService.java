package com.bookingsquadra.service;

import com.bookingsquadra.dto.LegalTermsDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class LegalService {

    private static final String TERMS_CONTENT = """
            Termos e Condições de Uso do Aplicativo Squadra
            Última atualização: 28 de abril de 2026

            Bem-vindo(a) ao Squadra. Estes Termos de Uso ("Termos") regem o seu acesso e uso do aplicativo móvel Squadra e serviços associados (coletivamente, o "Serviço").

            Ao baixar, acessar ou utilizar o Squadra, você concorda em ficar vinculado a estes Termos. Se você não concordar com qualquer parte destes termos, não deverá utilizar o nosso aplicativo.

            1. Descrição do Serviço e Papel de Intermediação

            O Squadra é uma plataforma tecnológica que atua exclusivamente como intermediadora entre os usuários (esportistas) e os estabelecimentos parceiros (donos ou locadores de quadras esportivas).

            Nosso objetivo é facilitar a busca, a marcação de horários e a comunicação entre as partes. O Squadra não é proprietário, operador ou administrador de nenhuma das quadras listadas no aplicativo.

            2. Cadastro e Segurança da Conta

            Elegibilidade: Para utilizar o Squadra para agendamentos, você deve ter a idade mínima legal para celebrar contratos ou utilizar o aplicativo sob a supervisão de um responsável legal.

            Informações Precisas: Você concorda em fornecer informações verdadeiras e exatas (como nome, telefone e e-mail) para viabilizar os agendamentos.

            Segurança: Você é responsável por manter a confidencialidade da sua conta e por todas as atividades realizadas nela.

            3. Agendamentos, Pagamentos e Taxas

            Processo de Reserva: Ao realizar um agendamento pelo aplicativo, você reserva um horário específico diretamente com o estabelecimento parceiro.

            Modalidades de Pagamento: Os pagamentos das locações são realizados no momento da reserva ou conforme a configuração do estabelecimento parceiro, suportando os métodos de PIX e Cartão de Crédito.

            Destinação dos Valores: Todo o valor pago pela locação da quadra é destinado ao proprietário/estabelecimento parceiro. O Squadra atua apenas como facilitador tecnológico dessa transação.

            Inexistência de Assinaturas: O uso do aplicativo pelo usuário final é isento de planos de assinatura ou mensalidades. Você paga apenas pelo horário que reservar junto ao estabelecimento.

            4. Cancelamentos e Reembolsos

            Políticas do Estabelecimento: As regras para cancelamento de horários, tolerância de atrasos, estornos e reagendamentos são definidas exclusivamente por cada estabelecimento parceiro.

            Responsabilidade pelo Reembolso: Como o Squadra repassa os valores aos proprietários das quadras, qualquer solicitação de reembolso decorrente de cancelamentos (dentro das regras do estabelecimento), chuva ou problemas estruturais no local deverá ser resolvida diretamente com o estabelecimento ou solicitada via aplicativo mediante as regras pré-estabelecidas por este parceiro.

            5. Limitação de Responsabilidade

            Por atuar apenas como plataforma de intermediação, o Squadra não se responsabiliza por:

            A qualidade, segurança, limpeza ou infraestrutura das quadras esportivas.

            Quaisquer acidentes, lesões corporais, furtos ou danos a bens materiais que possam ocorrer dentro das dependências dos estabelecimentos parceiros.

            Cancelamentos de última hora por parte do dono da quadra ou indisponibilidade por fatores externos (como falta de energia ou condições climáticas).

            Disputas financeiras decorrentes de negociações feitas fora da plataforma.

            6. Conduta do Usuário

            Ao utilizar o Squadra, você concorda em utilizar o sistema de agendamento de boa-fé. O cancelamento abusivo recorrente ou o não comparecimento ("no-show") nos horários reservados pode levar à suspensão temporária ou definitiva da sua conta no aplicativo, a fim de proteger os estabelecimentos parceiros.

            7. Privacidade e Proteção de Dados

            O tratamento de seus dados pessoais (como nome, contato e histórico de agendamentos) é realizado de acordo com a nossa Política de Privacidade, respeitando a Lei Geral de Proteção de Dados (LGPD). Os dados de contato são compartilhados com o estabelecimento parceiro apenas com a finalidade de viabilizar o agendamento e o acesso à quadra.

            8. Encerramento e Alterações

            Podemos suspender ou encerrar seu acesso ao aplicativo em caso de violação destes Termos. Além disso, podemos atualizar estes Termos periodicamente. O uso contínuo do Squadra após alterações significa que você aceita os novos Termos.

            9. Legislação Aplicável e Foro

            Estes Termos serão regidos pelas leis da República Federativa do Brasil. Fica eleito o foro da comarca de Uberlândia/MG para dirimir quaisquer controvérsias decorrentes destes Termos, com renúncia expressa a qualquer outro.

            10. Contato

            Para dúvidas, suporte ou solicitações relacionadas a estes Termos ou ao funcionamento do aplicativo, entre em contato através de:

            E-mail: contato@squadraplay.com

            Site: squadraplay.com
            """;

    public LegalTermsDto getTerms() {
        return new LegalTermsDto(
                "2026-04-28",
                "Termos e Condições de Uso do Aplicativo Squadra",
                TERMS_CONTENT,
                LocalDate.of(2026, 4, 28));
    }
}
