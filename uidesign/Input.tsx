import React from "react";
import styled, { css } from "styled-components";

const COLORS = {
  background: "#121212",
  surface: "#1E1E1E",
  onBackground: "#E0E0E0",
  primary: "#FFAB00",
  error: "#CF6679",
  disabled: "#404040",
};

type InputSize = "large" | "medium" | "small";

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  size?: InputSize;
  error?: boolean;
}

const sizeStyles = {
  large: css`
    font-size: 18px;
    padding: 14px 16px;
  `,
  medium: css`
    font-size: 16px;
    padding: 10px 12px;
  `,
  small: css`
    font-size: 14px;
    padding: 6px 8px;
  `,
};

const StyledInput = styled.input<{ $size: InputSize; $error?: boolean }>`
  background: ${COLORS.surface};
  color: ${COLORS.onBackground};
  border: 1.5px solid
    ${(props) => (props.$error ? COLORS.error : COLORS.onBackground)};
  border-radius: 6px;
  font-family: 'Noto Sans KR', 'Roboto', 'Apple SD Gothic Neo', sans-serif;
  outline: none;
  transition: border 0.2s, box-shadow 0.2s;
  ${(props) => sizeStyles[props.$size]}
  &:focus {
    border-color: ${COLORS.primary};
    box-shadow: 0 0 0 2px rgba(255,171,0,0.15);
  }
  &:disabled {
    background: ${COLORS.disabled};
    color: #888;
    cursor: not-allowed;
  }
`;

export const Input: React.FC<InputProps> = ({
  size = "medium",
  error = false,
  ...rest
}) => <StyledInput $size={size} $error={error} {...rest} />; 