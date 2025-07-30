import React from "react";
import styled from "styled-components";

const COLORS = {
  surface: "#1E1E1E",
  onBackground: "#E0E0E0",
};

interface CardProps {
  children: React.ReactNode;
  style?: React.CSSProperties;
}

const StyledCard = styled.div`
  background: ${COLORS.surface};
  color: ${COLORS.onBackground};
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.10);
  padding: 24px 20px;
  margin: 8px 0;
  font-family: 'Noto Sans KR', 'Roboto', 'Apple SD Gothic Neo', sans-serif;
`;

export const Card: React.FC<CardProps> = ({ children, style }) => (
  <StyledCard style={style}>{children}</StyledCard>
); 